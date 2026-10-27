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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import type { RequestOptions } from '@shared/services/api/BasePlatformClient';
import Hub from '@hubs/services/api/model/Hub';

export interface AddHubRequest {
  name: string;
  hostname: string;
  port: number;
  plaintext: boolean;
}

export default class HubClient extends BasePlatformClient {
  constructor() {
    super('/hubs');
  }

  async list(options?: RequestOptions): Promise<Hub[]> {
    return super.get<Hub[]>('', undefined, options);
  }

  async add(request: AddHubRequest): Promise<Hub> {
    return super.post<Hub>('', request);
  }

  async getById(hubId: string): Promise<Hub> {
    return super.get<Hub>(`/${hubId}`);
  }

  async delete(hubId: string): Promise<void> {
    return super.del<void>(`/${hubId}`);
  }
}
