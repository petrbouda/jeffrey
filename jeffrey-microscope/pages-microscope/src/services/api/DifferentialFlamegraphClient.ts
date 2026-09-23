/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import GlobalVars from '@/services/GlobalVars';
import RemoteFlamegraphClient from '@/services/api/RemoteFlamegraphClient';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import TimeRange from '@/services/api/model/TimeRange';
import GraphComponents from '@/services/api/model/GraphComponents';

export default class DifferentialFlamegraphClient extends RemoteFlamegraphClient {
  private readonly eventType: string;
  private readonly useWeight: boolean;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;

  constructor(
    primaryProfileId: string,
    secondaryProfileId: string,
    eventType: string,
    useWeight: boolean,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean
  ) {
    super(
      GlobalVars.internalUrl +
        '/profiles/' +
        primaryProfileId +
        '/diff/' +
        secondaryProfileId +
        '/differential-flamegraph'
    );
    this.eventType = eventType;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
  }

  // Differential Graph does not support Searching
  protected bothContent(
    components: GraphComponents,
    timeRange: TimeRange | null | undefined,
    search: string | null | undefined
  ): Record<string, unknown> {
    return {
      eventType: this.eventType,
      useWeight: this.useWeight,
      timeRange: timeRange,
      search: search,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    };
  }

  override provideTimeseries(_ignored: string | null): Promise<TimeseriesData> {
    // Differential flamegraph doesn't support search in timeseries, but we can still fetch timeseries
    // data. Delegates with explicit nulls so the payload keeps the `timeRange`/`search` keys present.
    return this.provideBoth(GraphComponents.TIMESERIES_ONLY, null, null).then(
      data => data.timeseries
    );
  }

  save(
    components: GraphComponents,
    flamegraphName: string,
    timeRange: TimeRange | null
  ): Promise<void> {
    return this.postRepository({
      flamegraphName: flamegraphName,
      eventType: this.eventType,
      timeRange: timeRange,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    });
  }
}
