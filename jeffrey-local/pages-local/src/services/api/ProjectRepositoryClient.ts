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
import RecordingSession from '@/services/api/model/RecordingSession.ts';
import RepositoryFile from '@/services/api/model/RepositoryFile.ts';
import RepositoryStatistics from '@/services/api/model/RepositoryStatistics.ts';

export default class ProjectRepositoryClient extends BasePlatformClient {
  constructor(workspaceId: string, projectId: string) {
    super(`/workspaces/${workspaceId}/projects/${projectId}/repository`);
  }

  listRecordingSessions(): Promise<RecordingSession[]> {
    return super.get<RecordingSession[]>('/sessions');
  }

  getRepositoryStatistics(): Promise<RepositoryStatistics> {
    return super.get<RepositoryStatistics>('/statistics');
  }

  copyRecordingSession(recordingSession: RecordingSession, merge: boolean): Promise<void> {
    const content = {
      sessionId: recordingSession.id,
      merge: merge
    };

    return super.post<void>('/sessions/download', content);
  }

  deleteRecordingSession(recordingSession: RecordingSession): Promise<void> {
    return super.del<void>('/sessions/' + recordingSession.id);
  }

  copySelectedRepositoryFile(
    sessionId: string,
    files: RepositoryFile[],
    merge: boolean
  ): Promise<void> {
    const ids: string[] = files.map(it => it.id);
    const content = {
      sessionId: sessionId,
      recordingIds: ids,
      merge: merge
    };

    return super.post<void>('/recordings/download', content);
  }

  deleteSelectedRepositoryFile(sessionId: string, files: RepositoryFile[]): Promise<void> {
    const ids: string[] = files.map(it => it.id);
    const content = {
      sessionId: sessionId,
      recordingIds: ids
    };

    return super.post<void>('/recordings/delete', content);
  }

  async downloadFile(sessionId: string, fileId: string): Promise<void> {
    const downloadUrl =
      this.baseUrl +
      '/sessions/' +
      sessionId +
      '/files/' +
      encodeURIComponent(fileId) +
      '/download';
    return HttpUtils.downloadFile(downloadUrl, fileId);
  }
}
