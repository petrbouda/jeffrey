/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import CreateWorkspaceRequest from '@/services/api/model/CreateWorkspaceRequest';
import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';

export default class WorkspaceClient extends BasePlatformClient {
  constructor() {
    super('/workspaces');
  }

  /**
   * Get all workspaces
   * GET /api/workspaces
   */
  async list(excludeRemote: boolean = false, options?: RequestOptions): Promise<Workspace[]> {
    return super.get<Workspace[]>('', { excludeRemote: excludeRemote }, options);
  }

  /**
   * Get a single workspace by ID
   * GET /api/workspaces/{workspaceId}
   */
  async getById(workspaceId: string): Promise<Workspace> {
    return super.get<Workspace>(`/${workspaceId}`);
  }

  /**
   * Create a new workspace
   * POST /api/workspaces
   */
  async create(request: CreateWorkspaceRequest): Promise<Workspace> {
    return super.post<Workspace>('', request);
  }

  /**
   * Delete a workspace by ID
   * DELETE /api/workspaces/{workspaceId}
   */
  async delete(workspaceId: string): Promise<void> {
    return super.del<void>(`/${workspaceId}`);
  }

  /**
   * Get events for a specific workspace
   * GET /api/workspaces/{workspaceId}/events
   */
  async getEvents(workspaceId: string): Promise<WorkspaceEvent[]> {
    return super.get<WorkspaceEvent[]>(`/${workspaceId}/events`);
  }
}
