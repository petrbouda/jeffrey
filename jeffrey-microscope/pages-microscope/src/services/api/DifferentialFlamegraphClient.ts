/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
