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

import BasePlatformClient from '@/services/api/BasePlatformClient';
import type { RequestOptions } from '@/services/api/BasePlatformClient';
import RemoteServer from '@/services/api/model/RemoteServer';

export interface AddRemoteServerRequest {
  name: string;
  hostname: string;
  port: number;
  plaintext: boolean;
}

export default class RemoteServerClient extends BasePlatformClient {
  constructor() {
    super('/remote-servers');
  }

  async list(options?: RequestOptions): Promise<RemoteServer[]> {
    return super.get<RemoteServer[]>('', undefined, options);
  }

  async add(request: AddRemoteServerRequest): Promise<RemoteServer> {
    return super.post<RemoteServer>('', request);
  }

  async getById(serverId: string): Promise<RemoteServer> {
    return super.get<RemoteServer>(`/${serverId}`);
  }

  async delete(serverId: string): Promise<void> {
    return super.del<void>(`/${serverId}`);
  }
}
