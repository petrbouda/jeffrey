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

const MARKDOWN_ACCEPT_HEADERS = {
  headers: {
    'Content-Type': 'application/json',
    Accept: 'text/markdown'
  },
  responseType: 'text' as const
};

export interface AiExportRequestParams {
  eventType: string;
  useWeight: boolean | null;
  useThreadMode: boolean;
  search: string | null;
  excludeNonJavaSamples: boolean;
  excludeIdleSamples: boolean;
  onlyUnsafeAllocationSamples: boolean;
}

export default class FlamegraphAiExportClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'flamegraph');
  }

  async generate(params: AiExportRequestParams): Promise<string> {
    const body = {
      flamegraphName: null,
      eventType: params.eventType,
      timeRange: null,
      search: params.search,
      useThreadMode: params.useThreadMode,
      useWeight: params.useWeight,
      excludeNonJavaSamples: params.excludeNonJavaSamples,
      excludeIdleSamples: params.excludeIdleSamples,
      onlyUnsafeAllocationSamples: params.onlyUnsafeAllocationSamples,
      threadInfo: null,
      components: GraphComponents.FLAMEGRAPH_ONLY,
      markers: null
    };
    const response = await axios.post<string>(
      `${this.baseUrl}/ai-export`,
      body,
      MARKDOWN_ACCEPT_HEADERS
    );
    return response.data;
  }
}
