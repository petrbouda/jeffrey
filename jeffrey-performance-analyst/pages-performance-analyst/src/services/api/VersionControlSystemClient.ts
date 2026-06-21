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
import type VersionControlSystemConfig from '@/services/api/model/VersionControlSystemConfig';

export interface VersionControlSystemSaveRequest {
  platform: string;
  url: string;
  // Leave empty on an update to preserve the previously stored token.
  token: string;
}

/**
 * Reads and writes the version-control integration (GitHub/GitLab) registered for a project.
 */
export default class VersionControlSystemClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string, projectId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}/version-control-system`);
  }

  load(): Promise<VersionControlSystemConfig> {
    return super.get<VersionControlSystemConfig>();
  }

  save(request: VersionControlSystemSaveRequest): Promise<VersionControlSystemConfig> {
    return super.put<VersionControlSystemConfig>('', request);
  }
}
