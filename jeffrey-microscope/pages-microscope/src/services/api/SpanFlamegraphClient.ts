/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

/**
 * Flamegraph client scoped to a single async-profiler span tag. Unlike {@link PrimaryFlamegraphClient}
 * it sends no time range or thread — the backend derives the scope from the tag's spans (their thread +
 * window), so the result contains only the samples those spans cover. The {@code timeRange}/{@code search}
 * arguments of the {@link FlamegraphClient} contract are ignored; the span scope fully defines the data.
 */
export default class SpanFlamegraphClient extends RemoteFlamegraphClient {
  private readonly tag: string;
  private readonly eventType: string;
  private useThreadMode: boolean;
  private useWeight: boolean | null;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;

  constructor(
    profileId: string,
    tag: string,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean
  ) {
    super(GlobalVars.internalUrl + '/profiles/' + profileId + '/async-profiler/spans/flamegraph');
    this.tag = tag;
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
      tag: this.tag,
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
