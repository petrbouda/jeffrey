/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
import GraphComponents from '@/services/api/model/GraphComponents';

/**
 * Flamegraph client scoped to a single async-profiler span. Like {@link SpanFlamegraphClient} it sends no
 * {@link TimeRange} or thread filter through the {@link FlamegraphClient} contract — instead it carries the
 * span's own interval (thread hash + start/end window), and the backend turns that into a single span
 * interval so the result contains only the samples this one span covers. The {@code threadHash} is kept as a
 * string to preserve full 64-bit precision over the wire.
 */
export default class SingleSpanFlamegraphClient extends RemoteFlamegraphClient {
  private readonly threadHash: string;
  private readonly fromMillis: number;
  private readonly toMillis: number;
  private readonly eventType: string;
  private useThreadMode: boolean;
  private useWeight: boolean | null;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;

  constructor(
    profileId: string,
    threadHash: string,
    fromMillis: number,
    toMillis: number,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean
  ) {
    super(
      GlobalVars.internalUrl + '/profiles/' + profileId + '/async-profiler/spans/single/flamegraph'
    );
    this.threadHash = threadHash;
    this.fromMillis = fromMillis;
    this.toMillis = toMillis;
    this.eventType = eventType;
    this.useThreadMode = useThreadMode;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
  }

  // The span scope fully defines the data — timeRange/search of the contract are ignored.
  protected bothContent(components: GraphComponents): Record<string, unknown> {
    return {
      threadHash: this.threadHash,
      fromMillis: this.fromMillis,
      toMillis: this.toMillis,
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: this.useThreadMode,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    };
  }

  save(): Promise<void> {
    return Promise.reject(new Error('Saving span-scoped flamegraphs is not supported'));
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
