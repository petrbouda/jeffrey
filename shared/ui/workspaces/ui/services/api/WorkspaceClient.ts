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
import Workspace from '@workspaces/services/api/model/Workspace';

export interface CreateWorkspaceRequest {
  referenceId: string;
  name: string;
}

/**
 * API client for workspace operations under a connected jeffrey-hub.
 * One instance per (hubId) — workspaces are listed live via gRPC.
 *
 * Shared between jeffrey-microscope and jeffrey-performance-analyst. Microscope's
 * event-log feature uses a separate app-local WorkspaceEventsClient.
 */
export default class WorkspaceClient extends BasePlatformClient {
  constructor(hubId: string) {
    super(`/hubs/${hubId}/workspaces`);
  }

  async list(options?: RequestOptions): Promise<Workspace[]> {
    return super.get<Workspace[]>('', undefined, options);
  }

  async getById(workspaceId: string): Promise<Workspace> {
    return super.get<Workspace>(`/${workspaceId}`);
  }

  async create(request: CreateWorkspaceRequest): Promise<Workspace> {
    return super.post<Workspace>('', request);
  }

  async delete(workspaceId: string): Promise<void> {
    return super.del<void>(`/${workspaceId}`);
  }
}
