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
import ThreadInfo from '@/services/api/model/ThreadInfo';
import TimeRange from '@/services/api/model/TimeRange';
import GraphComponents from '@/services/api/model/GraphComponents';

export default class PrimaryFlamegraphClient extends RemoteFlamegraphClient {
  private readonly eventType: string;
  private useThreadMode: boolean;
  private useWeight: boolean | null;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;
  private readonly threadInfo: ThreadInfo | null;
  private readonly traceId: string | null;
  private readonly spanId: string | null;

  constructor(
    profileId: string,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean,
    threadInfo: ThreadInfo | null,
    traceId: string | null = null,
    spanId: string | null = null
  ) {
    super(GlobalVars.internalUrl + '/profiles/' + profileId + '/flamegraph');
    this.eventType = eventType;
    this.useThreadMode = useThreadMode;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
    this.threadInfo = threadInfo;
    this.traceId = traceId;
    this.spanId = spanId;
  }

  protected bothContent(
    components: GraphComponents,
    timeRange: TimeRange | null | undefined,
    search: string | null | undefined
  ): Record<string, unknown> {
    const content: Record<string, unknown> = {
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: this.useThreadMode,
      timeRange: timeRange,
      search: search,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      threadInfo: this.threadInfo,
      components: components
    };
    if (this.traceId !== null) {
      content.traceId = this.traceId;
    }
    if (this.spanId !== null) {
      content.spanId = this.spanId;
    }
    return content;
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
      useThreadMode: this.useThreadMode,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    });
  }

  override supportsModeToggle(): boolean {
    return true;
  }

  override setUseThreadMode(value: boolean): void {
    this.useThreadMode = value;
  }

  override setUseWeight(value: boolean | null): void {
    this.useWeight = value;
  }
}
