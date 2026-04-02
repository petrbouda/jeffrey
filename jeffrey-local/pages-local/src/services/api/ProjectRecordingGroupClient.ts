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
import RecordingGroup from '@/services/api/model/RecordingGroup.ts';

export default class ProjectRecordingGroupClient extends BasePlatformClient {
  constructor(workspaceId: string, projectId: string) {
    super(`/workspaces/${workspaceId}/projects/${projectId}/recordings/groups`);
  }

  async create(groupName: string): Promise<RecordingGroup> {
    const requestBody = {
      groupName: groupName
    };
    return super.post<RecordingGroup>('', requestBody);
  }

  async list(): Promise<RecordingGroup[]> {
    return super.get<RecordingGroup[]>();
  }

  async delete(id: string) {
    return super.del('/' + id);
  }
}
