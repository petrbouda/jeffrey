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
import GraphComponents from '@/services/api/model/GraphComponents';
import TimeRange from '@/services/api/model/TimeRange';

export default class GuardianFlamegraphClient extends RemoteFlamegraphClient {
  private readonly eventType: string;
  private readonly useWeight: boolean;
  private readonly markers: any;

  constructor(profileId: string, eventType: string, useWeight: boolean, markers: any) {
    super(GlobalVars.internalUrl + '/profiles/' + profileId + '/flamegraph');
    this.eventType = eventType;
    this.useWeight = useWeight;
    this.markers = markers;
  }

  protected bothContent(
    components: GraphComponents,
    timeRange: TimeRange | null | undefined,
    search: string | null | undefined
  ): Record<string, unknown> {
    return {
      eventType: this.eventType,
      useWeight: this.useWeight,
      markers: this.markers,
      useThreadMode: false,
      timeRange: timeRange,
      search: search,
      excludeNonJavaSamples: false,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false,
      threadInfo: null,
      components: components
    };
  }

  // The flamegraph-only payload historically places `timeRange` before `useThreadMode`;
  // overridden to keep the serialized payload identical to the hand-built one.
  protected override flamegraphContent(timeRange: TimeRange | null): Record<string, unknown> {
    return {
      eventType: this.eventType,
      useWeight: this.useWeight,
      markers: this.markers,
      timeRange: timeRange,
      useThreadMode: false,
      excludeNonJavaSamples: false,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false,
      threadInfo: null,
      components: GraphComponents.FLAMEGRAPH_ONLY
    };
  }

  // The timeseries-only payload historically places `search` before `useThreadMode`;
  // overridden to keep the serialized payload identical to the hand-built one.
  protected override timeseriesContent(search: string | null): Record<string, unknown> {
    return {
      eventType: this.eventType,
      useWeight: this.useWeight,
      markers: this.markers,
      search: search,
      useThreadMode: false,
      excludeNonJavaSamples: false,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false,
      threadInfo: null,
      components: GraphComponents.TIMESERIES_ONLY
    };
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
      useWeight: this.useWeight,
      markers: this.markers,
      components: components
    });
  }
}
