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
import ConfigEntry, { ConfigType } from '@/services/api/model/ConfigEntry';

/** Configuration the Hub holds for one project's own scope. */
export default class ProjectConfigClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string, projectId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}/config`);
  }

  fetch(): Promise<ConfigEntry[]> {
    return super.get<ConfigEntry[]>();
  }

  upsert(type: ConfigType, value: string): Promise<ConfigEntry[]> {
    return super.put<ConfigEntry[]>(`/${type}`, { value });
  }

  delete(type: ConfigType): Promise<void> {
    return super.del<void>(`/${type}`);
  }
}
