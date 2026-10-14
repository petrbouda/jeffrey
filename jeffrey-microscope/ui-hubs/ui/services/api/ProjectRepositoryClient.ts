/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import HttpUtils from '@shared/services/HttpUtils';
import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RepositoryStatistics from '@hubs/services/api/model/RepositoryStatistics.ts';

export default class ProjectRepositoryClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string, projectId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}/repository`);
  }

  listRecordingSessions(): Promise<RecordingSession[]> {
    return super.get<RecordingSession[]>('/sessions');
  }

  getRepositoryStatistics(): Promise<RepositoryStatistics> {
    return super.get<RepositoryStatistics>('/statistics');
  }

  deleteRecordingSession(recordingSession: RecordingSession): Promise<void> {
    return super.del<void>('/sessions/' + recordingSession.id);
  }

  setSessionRetained(sessionId: string, retained: boolean): Promise<void> {
    return super.post<void>('/sessions/' + sessionId + '/retained', { retained: retained });
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
