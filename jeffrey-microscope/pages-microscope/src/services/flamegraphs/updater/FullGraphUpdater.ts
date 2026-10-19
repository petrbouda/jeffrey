/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import TimeRange from '@/services/api/model/TimeRange';
import FlamegraphClient from '@/services/api/FlamegraphClient.ts';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';

const MILLIS_PER_MINUTE = 60_000;

export default class FullGraphUpdater extends GraphUpdater {
  // A timeseries search is in flight: its loading states are on until it answers or is dropped
  private searchPending: boolean = false;

  constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
    super(httpClient, immediateInitialization);
  }

  public initialize(): void {
    if (!this.flamegraphRegistered) {
      return;
    }
    // With a timeseries, wait for its chart to register too (init is a barrier over both). Without
    // one (e.g. pprof), no timeseries chart is mounted, so proceed on the flamegraph alone.
    if (this.timeseriesEnabled && !this.timeseriesRegistered) {
      return;
    }

    // A search of the previous initialization (e.g. before a mode toggle) no longer applies
    this.dropPendingSearch();

    if (!this.timeseriesEnabled) {
      this.requestFlamegraph(
        'initialization',
        signal => this.httpClient.provide(this.linkedTimeRange(), signal),
        flamegraph => {
          this.flamegraph.onInit(flamegraph);
          this.applyLinkedSearch();
        }
      );
      return;
    }

    const upfront = this.initialTimeRangeUpfront();
    if (upfront !== null) {
      this.initializeInParallel(upfront.timeRange);
      return;
    }

    // The timeseries is the initialization's own: a zoom started meanwhile overtakes only the
    // flamegraph, the chart still opens on the timeseries
    const initialization = this.initializations.start();
    this.timeseries.onUpdateStarted();

    // First fetch only timeseries to know the data range, then the flamegraph with the calculated
    // range (zoomed or full). The flamegraph is a request like a zoom: a zoom or a reset started
    // meanwhile overtakes it.
    this.requestFlamegraph(
      'initialization',
      signal =>
        this.httpClient.provideTimeseries(null, initialization.signal).then(timeseries => {
          if (initialization.isLatest()) {
            // Initialize timeseries with full data (needed for brush chart)
            this.timeseries.onInit(timeseries);
            this.timeseries.onUpdateFinished();
          }

          // A window a link named, otherwise the range the visibleMinutes setting opens on
          const initialTimeRange =
            this.linkedTimeRange() ?? this.calculateInitialTimeRange(timeseries);
          return this.httpClient.provide(initialTimeRange, signal);
        }),
      flamegraph => {
        this.flamegraph.onInit(flamegraph);
        this.applyLinkedSearch();
      }
    );
  }

  /**
   * The window the first flamegraph opens on, when it is known without the timeseries: the one a link
   * named, the whole recording without an opening zoom, or the whole recording when it is no longer
   * than the opening window ({@link calculateInitialTimeRange} would answer null for it). Null when
   * only the timeseries can tell: the recording is longer, or the page does not know its duration.
   */
  private initialTimeRangeUpfront(): { timeRange: TimeRange | null } | null {
    const linked = this.linkedTimeRange();
    if (linked !== null) {
      return { timeRange: linked };
    }
    if (this.initialVisibleMinutes === null) {
      return { timeRange: null };
    }
    const openingWindowMillis = this.initialVisibleMinutes * MILLIS_PER_MINUTE;
    if (
      this.recordingDurationMillis !== null &&
      this.recordingDurationMillis <= openingWindowMillis
    ) {
      return { timeRange: null };
    }
    return null;
  }

  /**
   * Fetches the timeseries and the first flamegraph at once, for a window known up front. A linked
   * search draws into the chart as well, so it waits until the chart has opened on its timeseries.
   */
  private initializeInParallel(timeRange: TimeRange | null): void {
    const initialization = this.initializations.start();
    this.timeseries.onUpdateStarted();
    const chartOpened = this.httpClient
      .provideTimeseries(null, initialization.signal)
      .then(timeseries => {
        if (initialization.isLatest()) {
          this.timeseries.onInit(timeseries);
          this.timeseries.onUpdateFinished();
        }
      });
    chartOpened.catch(error => {
      if (initialization.isLatest()) {
        this.graphOperationFailed('initialization', error);
      }
    });

    this.requestFlamegraph(
      'initialization',
      signal => this.httpClient.provide(timeRange, signal),
      flamegraph => {
        this.flamegraph.onInit(flamegraph);
        chartOpened.then(
          () => this.applyLinkedSearch(),
          () => {}
        );
      }
    );
  }

  /**
   * Calculate the initial time range based on initialVisibleMinutes.
   * Returns null if full range should be used.
   * Note: Timeseries data is in seconds (relative to recording start).
   * Backend API expects milliseconds with absoluteTime=false for relative ranges.
   */
  private calculateInitialTimeRange(timeseries: TimeseriesData): TimeRange | null {
    if (this.initialVisibleMinutes === null) {
      return null;
    }

    if (!timeseries?.series?.length) {
      return null;
    }

    const series = timeseries.series[0];
    if (!series.data?.length) {
      return null;
    }

    // Timeseries data timestamps are in seconds
    const minTimeSeconds = series.data[0][0];
    const maxTimeSeconds = series.data[series.data.length - 1][0];
    const totalRangeSeconds = maxTimeSeconds - minTimeSeconds;
    const visibleRangeSeconds = this.initialVisibleMinutes * 60; // minutes to seconds

    // Only zoom if visible range is significantly smaller than total (less than 99%)
    if (visibleRangeSeconds >= totalRangeSeconds * 0.99) {
      return null;
    }

    // Convert to milliseconds for backend API (which expects milliseconds)
    // Use absoluteTime=false because this is relative to recording start
    return new TimeRange(
      Math.floor(minTimeSeconds * 1000),
      Math.ceil(Math.min(minTimeSeconds + visibleRangeSeconds, maxTimeSeconds) * 1000),
      false
    );
  }

  public updateWithZoom(timeRange: TimeRange): void {
    this.timeseries.onZoom();
    this.requestFlamegraph(
      'zoom',
      signal => this.httpClient.provide(timeRange, signal),
      data => this.flamegraph.onZoom(data)
    );
  }

  public resetZoom(): void {
    this.timeseries.onResetZoom();
    this.requestFlamegraph(
      'zoom reset',
      signal => this.httpClient.provide(null, signal),
      data => this.flamegraph.onResetZoom(data)
    );
  }

  public updateWithSearch(expression: string): void {
    const search = this.searchRequests.start();
    this.flamegraph.onUpdateStarted();
    this.searchBar.onUpdateStarted();

    if (this.timeseriesSearchEnabled) {
      this.searchPending = true;
      this.timeseries.onUpdateStarted();

      this.httpClient
        .provideTimeseries(expression, search.signal)
        .then(data => {
          // A later search has taken over, or a reset or a re-initialization dropped this one
          if (!search.isLatest()) {
            return;
          }
          this.searchPending = false;
          this.flamegraph.onSearch(expression);
          this.timeseries.onSearch(data);

          this.flamegraph.onUpdateFinished();
          this.timeseries.onUpdateFinished();
          this.searchBar.onUpdateFinished();
        })
        .catch(error => {
          if (search.isLatest()) {
            this.searchPending = false;
            this.graphOperationFailed('search', error);
          }
        });
    } else {
      // Only search in flamegraph, no timeseries update
      this.flamegraph.onSearch(expression);
      this.flamegraph.onUpdateFinished();
      this.searchBar.onUpdateFinished();
    }
  }

  public resetSearch(): void {
    // A search still in flight must not bring its highlight back after the reset
    this.dropPendingSearch();
    this.flamegraph.onResetSearch();
    this.timeseries.onResetSearch();
    this.searchBar.onMatched(null);
  }

  /**
   * Drops the search in flight, if any, and releases the loading states it switched on, as its answer
   * no longer will.
   */
  private dropPendingSearch(): void {
    this.searchRequests.invalidate();
    if (this.searchPending) {
      this.searchPending = false;
      this.flamegraph.onUpdateFinished();
      this.timeseries.onUpdateFinished();
      this.searchBar.onUpdateFinished();
    }
  }

  public updateModes(useThreadMode: boolean, useWeight: boolean): void {
    if (this.httpClient.supportsModeToggle()) {
      this.httpClient.setUseThreadMode(useThreadMode);
      this.httpClient.setUseWeight(useWeight);
      this.initialize();
    }
  }
}
