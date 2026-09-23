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
      components: GraphComponents.FLAMEGRAPH_ONLY
    };
    const response = await axios.post<string>(
      `${this.baseUrl}/ai-export`,
      body,
      MARKDOWN_ACCEPT_HEADERS
    );
    return response.data;
  }
}
