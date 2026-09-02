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
