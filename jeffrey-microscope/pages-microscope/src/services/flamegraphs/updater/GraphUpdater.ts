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

import FlamegraphData from '@/services/api/model/FlamegraphData';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import TimeRange from '@/services/api/model/TimeRange';
import FlamegraphClient from '@/services/api/FlamegraphClient';

export default abstract class GraphUpdater {
  /**
   * How long a modal waits after swapping a flamegraph view in before calling initialize():
   * the graph component has to render and register its callbacks here first. One constant for
   * every modal-hosted flamegraph, so the timing cannot drift apart per view.
   */
  public static readonly MODAL_INIT_DELAY_MS = 200;

  protected flamegraphRegistered: boolean = false;
  protected timeseriesRegistered: boolean = false;
  protected httpClient: FlamegraphClient;
  private readonly immediateInitialization: boolean = false;

  protected constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
    this.httpClient = httpClient;
    this.immediateInitialization = immediateInitialization;
  }

  protected flamegraphOnUpdateStartedCallback: () => void = () => {};

  protected flamegraphOnUpdateFinishedCallback: () => void = () => {};

  protected flamegraphOnInitCallback: (data: FlamegraphData) => void = () => {};

  protected flamegraphOnSearchCallback: (data: string) => void = () => {};

  protected flamegraphOnResetSearchCallback: () => void = () => {};

  protected flamegraphOnZoomCallback: (data: FlamegraphData) => void = () => {};

  protected flamegraphOnResetZoomCallback: (data: FlamegraphData) => void = () => {};

  protected timeseriesOnUpdateStartedCallback: () => void = () => {};

  protected timeseriesOnUpdateFinishedCallback: () => void = () => {};

  protected timeseriesOnInitCallback: (data: TimeseriesData) => void = () => {};

  protected timeseriesOnSearchCallback: (data: TimeseriesData) => void = () => {};

  protected timeseriesOnResetSearchCallback: () => void = () => {};

  protected timeseriesOnZoomCallback: (data: void) => void = () => {};

  protected timeseriesOnResetZoomCallback: () => void = () => {};

  protected timeseriesControlResetZoomCallback: () => void = () => {};

  protected searchBarOnMatchedCallback: (matched: string | null) => void = () => {};

  protected searchBarOnUpdateStartedCallback: () => void = () => {};

  protected searchBarOnUpdateFinishedCallback: () => void = () => {};

  protected timeseriesSearchEnabled: boolean = true;
  // When false the graph has no timeseries at all (no chart is mounted, so no timeseries callbacks
  // ever register). Initialization must then proceed on the flamegraph alone. Used for aggregated
  // recordings without a time dimension (e.g. pprof).
  protected timeseriesEnabled: boolean = true;
  protected initialVisibleMinutes: number | null = null;

  public setTimeseriesSearchEnabled(enabled: boolean): void {
    this.timeseriesSearchEnabled = enabled;
  }

  public setTimeseriesEnabled(enabled: boolean): void {
    this.timeseriesEnabled = enabled;
  }

  public setInitialVisibleMinutes(minutes: number): void {
    this.initialVisibleMinutes = minutes;
  }

  /**
   * Terminal handler for every request chain an updater starts. The started callbacks have
   * already switched the flamegraph/timeseries panels into their loading state, so a rejected
   * promise without this handler leaves them spinning forever and swallows the reason. Prints
   * the full error — message, stacktrace and, for a failed request, the decoded server body
   * attached as the cause — and releases every loading state; the finished callbacks are safe
   * no-ops for panels that never started.
   */
  protected graphOperationFailed(operation: string, error: unknown): void {
    console.error(`Graph ${operation} failed:`, error);
    this.flamegraphOnUpdateFinishedCallback();
    this.timeseriesOnUpdateFinishedCallback();
    this.searchBarOnUpdateFinishedCallback();
  }

  public registerSearchBarCallbacks(
    onUpdateStarted: () => void,
    onUpdateFinished: () => void,
    onMatched: (matched: string | null) => void
  ): void {
    this.searchBarOnUpdateStartedCallback = onUpdateStarted;
    this.searchBarOnUpdateFinishedCallback = onUpdateFinished;
    this.searchBarOnMatchedCallback = onMatched;
  }

  public reportMatched(matched: string | null): void {
    this.searchBarOnMatchedCallback(matched);
  }

  public registerTimeseriesControlCallbacks(onResetZoom: () => void): void {
    this.timeseriesControlResetZoomCallback = onResetZoom;
  }

  public resetTimeseriesZoom(): void {
    this.timeseriesControlResetZoomCallback();
    this.resetZoom();
  }

  public registerFlamegraphCallbacks(
    onUpdateStarted: () => void,
    onUpdateFinished: () => void,
    onInit: (data: FlamegraphData) => void,
    onSearch: (data: string) => void,
    onResetSearch: () => void,
    onZoom: (data: FlamegraphData) => void,
    onResetZoom: (data: FlamegraphData) => void
  ): void {
    this.flamegraphOnUpdateStartedCallback = onUpdateStarted;
    this.flamegraphOnUpdateFinishedCallback = onUpdateFinished;
    this.flamegraphOnInitCallback = onInit;
    this.flamegraphOnSearchCallback = onSearch;
    this.flamegraphOnResetSearchCallback = onResetSearch;
    this.flamegraphOnZoomCallback = onZoom;
    this.flamegraphOnResetZoomCallback = onResetZoom;

    this.flamegraphRegistered = true;
    if (this.immediateInitialization) {
      this.initialize();
    }
  }

  public registerTimeseriesCallbacks(
    onUpdateStarted: () => void,
    onUpdateFinished: () => void,
    onInit: (data: TimeseriesData) => void,
    onSearch: (data: TimeseriesData) => void,
    onResetSearch: () => void,
    onZoom: (data: void) => void,
    onResetZoom: () => void
  ): void {
    this.timeseriesOnUpdateStartedCallback = onUpdateStarted;
    this.timeseriesOnUpdateFinishedCallback = onUpdateFinished;
    this.timeseriesOnInitCallback = onInit;
    this.timeseriesOnSearchCallback = onSearch;
    this.timeseriesOnResetSearchCallback = onResetSearch;
    this.timeseriesOnZoomCallback = onZoom;
    this.timeseriesOnResetZoomCallback = onResetZoom;

    this.timeseriesRegistered = true;
    if (this.immediateInitialization) {
      this.initialize();
    }
  }

  abstract initialize(): void;

  abstract updateWithZoom(timeRange: TimeRange): void;

  abstract resetZoom(): void;

  abstract updateWithSearch(expression: string): void;

  abstract resetSearch(): void;

  abstract updateModes(useThreadMode: boolean, useWeight: boolean): void;
}
