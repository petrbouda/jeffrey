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
import FlamegraphClient from '@/services/api/FlamegraphClient';
import TimeRange from '@/services/api/model/TimeRange';

export default class OnlyFlamegraphGraphUpdater extends GraphUpdater {
  private readonly timeRange: TimeRange | null;

  constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
    super(httpClient, immediateInitialization);
    this.timeRange = null;
  }

  public initialize(): void {
    this.requestFlamegraph(
      'initialization',
      signal => this.httpClient.provide(this.timeRange, signal),
      data => this.flamegraph.onInit(data)
    );
  }

  public updateWithZoom(timeRange: TimeRange): void {
    this.requestFlamegraph(
      'zoom',
      signal => this.httpClient.provide(timeRange, signal),
      data => this.flamegraph.onZoom(data)
    );
  }

  public resetZoom(): void {
    this.requestFlamegraph(
      'zoom reset',
      signal => this.httpClient.provide(null, signal),
      data => this.flamegraph.onResetZoom(data)
    );
  }

  public updateWithSearch(expression: string): void {
    this.flamegraph.onUpdateStarted();
    this.flamegraph.onSearch(expression);
    this.flamegraph.onUpdateFinished();
  }

  public resetSearch(): void {
    this.flamegraph.onResetSearch();
  }

  public updateModes(useThreadMode: boolean, useWeight: boolean): void {
    if (this.httpClient.supportsModeToggle()) {
      this.httpClient.setUseThreadMode(useThreadMode);
      this.httpClient.setUseWeight(useWeight);
      this.initialize();
    }
  }
}
