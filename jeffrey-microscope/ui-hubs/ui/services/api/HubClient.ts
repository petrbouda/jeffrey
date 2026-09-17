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
