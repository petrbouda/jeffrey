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
  /**
   * Set instead of threadInfo when the graph is opened from a collapsed timeline lane. The lane has
   * no thread of its own and the page holds at most a slice of its members, so it names the group
   * and the server resolves every thread behind it.
   */
  private readonly threadGroup: string | null;

  constructor(
    profileId: string,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean,
    threadInfo: ThreadInfo | null,
    threadGroup: string | null = null
  ) {
    super(GlobalVars.internalUrl + '/profiles/' + profileId + '/flamegraph');
    this.eventType = eventType;
    this.useThreadMode = useThreadMode;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
    this.threadInfo = threadInfo;
    this.threadGroup = threadGroup;
  }

  protected bothContent(
    components: GraphComponents,
    timeRange: TimeRange | null | undefined,
    search: string | null | undefined
  ): Record<string, unknown> {
    return {
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: this.useThreadMode,
      timeRange: timeRange,
      search: search,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      threadInfo: this.threadInfo,
      threadGroup: this.threadGroup,
      components: components
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
