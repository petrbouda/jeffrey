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

  // Same stance as SingleSpanFlamegraphClient: a scoped flamegraph is a transient view, not a
  // repository artifact, so saving is explicitly unsupported rather than silently absent.
  save(): Promise<void> {
    return Promise.reject(new Error('Saving trace-scoped flamegraphs is not supported'));
  }
}
