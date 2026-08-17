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
}
