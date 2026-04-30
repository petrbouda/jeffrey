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
import Workspace from '@/services/api/model/Workspace';

export interface CreateWorkspaceRequest {
  referenceId: string;
  name: string;
}

/**
 * API client for workspace operations under a connected jeffrey-server.
 * One instance per (serverId) — workspaces are listed live via gRPC.
 */
export default class WorkspaceClient extends BasePlatformClient {
  constructor(serverId: string) {
    super(`/remote-servers/${serverId}/workspaces`);
  }

  async list(options?: RequestOptions): Promise<Workspace[]> {
    return super.get<Workspace[]>('', undefined, options);
  }

  async create(request: CreateWorkspaceRequest): Promise<Workspace> {
    return super.post<Workspace>('', request);
  }

  async delete(workspaceId: string): Promise<void> {
    return super.del<void>(`/${workspaceId}`);
  }
}
