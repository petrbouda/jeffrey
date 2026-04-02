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
import HttpUtils from '@/services/HttpUtils';
import Recording from '@/services/api/model/Recording';

export default class ProjectRecordingClient extends BasePlatformClient {
  constructor(workspaceId: string, projectId: string) {
    super(`/workspaces/${workspaceId}/projects/${projectId}/recordings`);
  }

  async list(): Promise<Recording[]> {
    return super.get<Recording[]>();
  }

  async delete(id: string): Promise<void> {
    return super.del<void>(`/${id}`);
  }

  async moveToGroup(recordingId: string, groupId: string | null): Promise<void> {
    return super.put<void>(`/${recordingId}/group`, { groupId });
  }

  async downloadFile(recordingId: string, fileId: string): Promise<void> {
    const downloadUrl =
      this.baseUrl + '/' + recordingId + '/files/' + encodeURIComponent(fileId) + '/download';
    return HttpUtils.downloadFile(downloadUrl, fileId);
  }
}
