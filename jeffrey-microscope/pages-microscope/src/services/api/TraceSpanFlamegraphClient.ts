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
 * Flamegraph client scoped to one span of a trace.
 *
 * The span is addressed by the path, so unlike the async-profiler span clients this sends no
 * interval of its own — the backend resolves the span's window, and cuts its children out of it
 * when `selfOnly` is set. That is the difference between "this span took 400 ms" and "this span
 * spent 400 ms in code it owns".
 */
export default class TraceSpanFlamegraphClient extends RemoteFlamegraphClient {
  private readonly selfOnly: boolean;
  private readonly eventType: string;
  private readonly useWeight: boolean | null;

  constructor(
    profileId: string,
    traceId: string,
    spanId: string,
    selfOnly: boolean,
    eventType: string,
    useWeight: boolean | null
  ) {
    super(
      GlobalVars.internalUrl +
        '/profiles/' +
        profileId +
        '/traces/' +
        traceId +
        '/spans/' +
        spanId +
        '/flamegraph'
    );
    this.selfOnly = selfOnly;
    this.eventType = eventType;
    this.useWeight = useWeight;
  }

  // The span scope fully defines the data — timeRange/search of the contract are ignored.
  protected bothContent(components: GraphComponents): Record<string, unknown> {
    return {
      selfOnly: this.selfOnly,
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: false,
      excludeNonJavaSamples: false,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false,
      components: components
    };
  }
}
