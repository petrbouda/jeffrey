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
import type { TraceOperationId } from '@/services/api/model/trace/TraceModels';

/**
 * Flamegraph client scoped to one trace type. Sends no time range or thread — the backend derives
 * the scope from the traces of that type, so the result contains only the samples those traces
 * cover. The `timeRange`/`search` arguments of the `FlamegraphClient` contract are ignored.
 *
 * The scope is the whole operation identity, not just its name: an inbound `GET /orders` and an
 * outbound call to the same path would otherwise be drawn into one graph.
 */
export default class TraceOperationFlamegraphClient extends RemoteFlamegraphClient {
  private readonly operation: TraceOperationId;
  private readonly eventType: string;
  private readonly useThreadMode: boolean;
  private readonly useWeight: boolean | null;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;

  constructor(
    profileId: string,
    operation: TraceOperationId,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean
  ) {
    super(GlobalVars.internalUrl + '/profiles/' + profileId + '/traces/operation/flamegraph');
    this.operation = operation;
    this.eventType = eventType;
    this.useThreadMode = useThreadMode;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
  }

  // The operation scope fully defines the data — timeRange/search of the contract are ignored.
  protected bothContent(components: GraphComponents): Record<string, unknown> {
    return {
      name: this.operation.name,
      kind: this.operation.kind,
      // Named apart from `eventType`, which selects the samples to draw rather than the trace type.
      rootEventType: this.operation.eventType,
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: this.useThreadMode,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    };
  }

  // Same stance as SingleSpanFlamegraphClient: a scoped flamegraph is a transient view, not a
  // repository artifact, so saving is explicitly unsupported rather than silently absent.
  save(): Promise<void> {
    return Promise.reject(new Error('Saving trace-scoped flamegraphs is not supported'));
  }
}

