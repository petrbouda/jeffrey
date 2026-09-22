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
import ScopedConfig, { ConfigScope, ConfigType } from '@/services/api/model/ScopedConfig';

/**
 * Configuration the Hub holds for a workspace and for every scope around it.
 *
 * The Hub stores typed values and renders them into the file the Provisioner reads, so what this
 * edits and what a JVM merges are the same thing described by the same side.
 */
export default class WorkspaceConfigClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/config`);
  }

  /** The global scope, this workspace's own and every project's, in merge order. */
  list(): Promise<ScopedConfig[]> {
    return super.get<ScopedConfig[]>();
  }

  /** Scope must be GLOBAL or WORKSPACE; a project's value is edited through the project. */
  upsert(scope: ConfigScope, type: ConfigType, value: string): Promise<ScopedConfig> {
    return super.put<ScopedConfig>(`/${scope}/${type}`, { value });
  }

  delete(scope: ConfigScope, type: ConfigType): Promise<void> {
    return super.del<void>(`/${scope}/${type}`);
  }
}
