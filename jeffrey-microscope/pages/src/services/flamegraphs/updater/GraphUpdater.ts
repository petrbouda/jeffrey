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
import type { LinkedGraphState } from '@/services/flamegraphs/FlamegraphLinkQuery';
import LatestRequest from '@/services/flamegraphs/updater/LatestRequest';

/** What the flamegraph panel does as the updater drives it. */
export interface FlamegraphCallbacks {
  onUpdateStarted(): void;
  onUpdateFinished(): void;
  onInit(data: FlamegraphData): void;
  onSearch(expression: string): void;
  onResetSearch(): void;
  onZoom(data: FlamegraphData): void;
  onResetZoom(data: FlamegraphData): void;
}

/** What the timeseries chart does as the updater drives it. */
export interface TimeseriesCallbacks {
  onUpdateStarted(): void;
  onUpdateFinished(): void;
  onInit(data: TimeseriesData): void;
  onSearch(data: TimeseriesData): void;
  onResetSearch(): void;
  onZoom(): void;
  onResetZoom(): void;
}

/** What the search bar does as the updater drives it. */
export interface SearchBarCallbacks {
  onUpdateStarted(): void;
  onUpdateFinished(): void;
  /** The share of the graph the search matched, null once the search is reset. */
  onMatched(matched: string | null): void;
}

const NONE = () => {};

export default abstract class GraphUpdater {
  /**
   * How long a modal waits after swapping a flamegraph view in before calling initialize():
   * the graph component has to render and register its callbacks here first. One constant for
   * every modal-hosted flamegraph, so the timing cannot drift apart per view.
   */
  public static readonly MODAL_INIT_DELAY_MS = 200;

  // Only the answer to the latest request of a kind is applied, whatever order the answers arrive
  // in: the flamegraph of an initialization, zoom or zoom reset; the timeseries an initialization
  // opens the chart with; and a search, which a reset or a re-initialization drops while in flight.
  protected readonly flamegraphRequests = new LatestRequest();
  protected readonly initializations = new LatestRequest();
  protected readonly searchRequests = new LatestRequest();

  // A panel that has not registered yet is driven as one that does nothing
  protected flamegraph: FlamegraphCallbacks = {
    onUpdateStarted: NONE,
    onUpdateFinished: NONE,
    onInit: NONE,
    onSearch: NONE,
    onResetSearch: NONE,
    onZoom: NONE,
    onResetZoom: NONE
  };
  protected timeseries: TimeseriesCallbacks = {
    onUpdateStarted: NONE,
    onUpdateFinished: NONE,
    onInit: NONE,
    onSearch: NONE,
    onResetSearch: NONE,
    onZoom: NONE,
    onResetZoom: NONE
  };
  protected searchBar: SearchBarCallbacks = {
    onUpdateStarted: NONE,
    onUpdateFinished: NONE,
    onMatched: NONE
  };

  protected flamegraphRegistered: boolean = false;
  protected timeseriesRegistered: boolean = false;
  protected httpClient: FlamegraphClient;
  private readonly immediateInitialization: boolean = false;

  protected constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
    this.httpClient = httpClient;
    this.immediateInitialization = immediateInitialization;
  }

  protected timeseriesControlResetZoomCallback: () => void = () => {};

  protected timeseriesSearchEnabled: boolean = true;
  // When false the graph has no timeseries at all (no chart is mounted, so no timeseries callbacks
  // ever register). Initialization must then proceed on the flamegraph alone. Used for aggregated
  // recordings without a time dimension (e.g. pprof).
  protected timeseriesEnabled: boolean = true;
  protected initialVisibleMinutes: number | null = null;
  // How long the recording ran, when the page knows it: a recording no longer than the opening
  // window opens on all of it, so the first flamegraph need not wait for the timeseries
  protected recordingDurationMillis: number | null = null;
  // Where a link opened the graph: a window that replaces the opening zoom, and a search applied
  // once, after the first drawing. Both null unless a link named them.
  private linkedState: LinkedGraphState = { timeRange: null, search: null };
  private linkedSearchPending: boolean = false;

  public setTimeseriesSearchEnabled(enabled: boolean): void {
    this.timeseriesSearchEnabled = enabled;
  }

  public setTimeseriesEnabled(enabled: boolean): void {
    this.timeseriesEnabled = enabled;
  }

  public setInitialVisibleMinutes(minutes: number): void {
    this.initialVisibleMinutes = minutes;
  }

  public setRecordingDuration(durationMillis: number | null): void {
    this.recordingDurationMillis = durationMillis;
  }

  /**
   * Opens the graph on the state a link named instead of the default view. Called before the
   * components register, so the first drawing is already the linked one.
   */
  public openAt(state: LinkedGraphState): void {
    this.linkedState = state;
    this.linkedSearchPending = state.search != null;
  }

  /** The window a link opened the graph on, as the relative range the requests take; null without one. */
  public linkedTimeRange(): TimeRange | null {
    return this.linkedState.timeRange;
  }

  /** The search a link opened the graph with, for the search box to show; null without one. */
  public linkedSearch(): string | null {
    return this.linkedState.search;
  }

  /**
   * Runs the linked search the first time the graph is drawn, the way the search box runs one. Only
   * once: a later re-initialisation (a mode toggle) keeps whatever search the reader has since left.
   */
  protected applyLinkedSearch(): void {
    if (!this.linkedSearchPending || this.linkedState.search == null) {
      return;
    }
    this.linkedSearchPending = false;
    this.updateWithSearch(this.linkedState.search);
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
    this.flamegraph.onUpdateFinished();
    this.timeseries.onUpdateFinished();
    this.searchBar.onUpdateFinished();
  }

  /**
   * Runs one flamegraph request and hands its answer to `apply`, unless a later flamegraph request
   * started in the meantime: then this one is aborted, its answer or failure is dropped, and the
   * later request releases the loading state.
   */
  protected requestFlamegraph(
    operation: string,
    request: (signal: AbortSignal) => Promise<FlamegraphData>,
    apply: (data: FlamegraphData) => void
  ): void {
    const ticket = this.flamegraphRequests.start();
    this.flamegraph.onUpdateStarted();
    request(ticket.signal)
      .then(data => {
        if (ticket.isLatest()) {
          apply(data);
          this.flamegraph.onUpdateFinished();
        }
      })
      .catch(error => {
        // An overtaken request is aborted on purpose; its failure is no failure of the graph
        if (ticket.isLatest()) {
          this.graphOperationFailed(operation, error);
        }
      });
  }

  public registerSearchBarCallbacks(callbacks: SearchBarCallbacks): void {
    this.searchBar = callbacks;
  }

  public reportMatched(matched: string | null): void {
    this.searchBar.onMatched(matched);
  }

  public registerTimeseriesControlCallbacks(onResetZoom: () => void): void {
    this.timeseriesControlResetZoomCallback = onResetZoom;
  }

  public resetTimeseriesZoom(): void {
    this.timeseriesControlResetZoomCallback();
    this.resetZoom();
  }

  public registerFlamegraphCallbacks(callbacks: FlamegraphCallbacks): void {
    this.flamegraph = callbacks;

    this.flamegraphRegistered = true;
    if (this.immediateInitialization) {
      this.initialize();
    }
  }

  public registerTimeseriesCallbacks(callbacks: TimeseriesCallbacks): void {
    this.timeseries = callbacks;

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
