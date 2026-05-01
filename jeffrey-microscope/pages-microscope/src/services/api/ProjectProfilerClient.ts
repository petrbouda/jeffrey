/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import ProfilerSettings from '@/services/api/model/ProfilerSettings.ts';

/**
 * Client for project-level profiler settings API.
 * Works for both LIVE and REMOTE workspaces - the backend handles the delegation.
 */
export default class ProjectProfilerClient extends BasePlatformClient {
  constructor(serverId: string, workspaceId: string, projectId: string) {
    super(`/remote-servers/${serverId}/workspaces/${workspaceId}/projects/${projectId}/profiler/settings`);
  }

  /**
   * Fetch effective settings for a project (resolved from hierarchy: project > workspace > global)
   * Works for both LIVE and REMOTE workspaces.
   */
  fetch(): Promise<ProfilerSettings> {
    return super.get<ProfilerSettings>();
  }

  /**
   * Upsert project-level settings
   * Works for both LIVE and REMOTE workspaces.
   */
  upsert(agentSettings: string): Promise<void> {
    const content = {
      agentSettings: agentSettings
    };
    return super.post<void>('', content);
  }

  /**
   * Delete project-level settings
   * Works for both LIVE and REMOTE workspaces.
   */
  delete(): Promise<void> {
    return super.del<void>();
  }
}
