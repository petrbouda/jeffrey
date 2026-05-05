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

export interface CurrentProfilerSettings {
  workspaceAgentSettings: string | null;
  globalAgentSettings: string | null;
}

/**
 * API client for workspace-level profiler settings on a connected jeffrey-server.
 * One instance per (serverId, workspaceId).
 */
export default class WorkspaceProfilerSettingsClient extends BasePlatformClient {
  constructor(serverId: string, workspaceId: string) {
    super(`/remote-servers/${serverId}/workspaces/${workspaceId}/profiler/settings`);
  }

  async fetchCurrent(): Promise<CurrentProfilerSettings> {
    return super.get<CurrentProfilerSettings>('');
  }

  async upsert(agentSettings: string): Promise<void> {
    return super.post<void>('', { agentSettings });
  }

  async delete(): Promise<void> {
    return super.del<void>('');
  }
}
