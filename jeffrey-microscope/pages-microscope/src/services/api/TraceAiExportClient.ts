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

import axios from 'axios';
import BaseProfileClient from '@/services/api/BaseProfileClient';
import GraphComponents from '@/services/api/model/GraphComponents';

/**
 * Asks for Markdown rather than JSON, because the response is a document written for a model to
 * read — the semantics of self time, the critical path and JVM context are prose, and rendering them
 * lives on the server where those semantics are known.
 */
const MARKDOWN_ACCEPT_HEADERS = {
  headers: {
    Accept: 'text/markdown'
  },
  responseType: 'text' as const
};

/**
 * A span's flamegraph, the way the trace drill-down asks for it: the span is addressed by the path,
 * and the backend resolves its window — cut down to the span's own work when `selfOnly` is set.
 */
export interface SpanFlamegraphAiExportParams {
  selfOnly: boolean;
  eventType: string;
  useWeight: boolean;
  useThreadMode: boolean;
  excludeNonJavaSamples: boolean;
  excludeIdleSamples: boolean;
  onlyUnsafeAllocationSamples: boolean;
}

export default class TraceAiExportClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'traces');
  }

  async generateTrace(traceId: string): Promise<string> {
    const response = await axios.get<string>(
      `${this.baseUrl}/${traceId}/ai-export`,
      MARKDOWN_ACCEPT_HEADERS
    );
    return response.data;
  }

  async generateOperation(name: string, kind: string, eventType: string): Promise<string> {
    const response = await axios.get<string>(`${this.baseUrl}/operation/ai-export`, {
      ...MARKDOWN_ACCEPT_HEADERS,
      params: { name, kind, eventType }
    });
    return response.data;
  }

  /**
   * The flamegraph of one span rendered for an AI — the same samples the span's graph draws, so an
   * export taken over an open graph describes the frames on screen rather than the trace around them.
   */
  async generateSpanFlamegraph(
    traceId: string,
    spanId: string,
    params: SpanFlamegraphAiExportParams
  ): Promise<string> {
    const body = {
      selfOnly: params.selfOnly,
      eventType: params.eventType,
      useThreadMode: params.useThreadMode,
      useWeight: params.useWeight,
      excludeNonJavaSamples: params.excludeNonJavaSamples,
      excludeIdleSamples: params.excludeIdleSamples,
      onlyUnsafeAllocationSamples: params.onlyUnsafeAllocationSamples,
      components: GraphComponents.FLAMEGRAPH_ONLY
    };
    const response = await axios.post<string>(
      `${this.baseUrl}/${traceId}/spans/${spanId}/flamegraph/ai-export`,
      body,
      {
        ...MARKDOWN_ACCEPT_HEADERS,
        headers: { ...MARKDOWN_ACCEPT_HEADERS.headers, 'Content-Type': 'application/json' }
      }
    );
    return response.data;
  }
}
