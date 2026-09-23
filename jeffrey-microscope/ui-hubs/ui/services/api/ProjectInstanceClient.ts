/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import ProjectInstance from '@hubs/services/api/model/ProjectInstance';
import ProjectInstanceDetail from '@hubs/services/api/model/ProjectInstanceDetail';
import ProjectInstanceSession from '@hubs/services/api/model/ProjectInstanceSession';
import ProjectInstanceSessionDetail from '@hubs/services/api/model/ProjectInstanceSessionDetail';

export default class ProjectInstanceClient extends BasePlatformClient {
  constructor(hubId: string, workspaceId: string, projectId: string) {
    super(`/hubs/${hubId}/workspaces/${workspaceId}/projects/${projectId}/instances`);
  }

  async list(includeSessions: boolean = false): Promise<ProjectInstance[]> {
    const params = includeSessions ? { includeSessions: true } : undefined;
    return super.get<any[]>('', params).then(data => data.map(this.mapToInstance));
  }

  async find(instanceId: string): Promise<ProjectInstance | undefined> {
    return super
      .get<any>(`/${instanceId}`)
      .then(data => this.mapToInstance(data))
      .catch(() => undefined);
  }

  async getSessions(instanceId: string): Promise<ProjectInstanceSession[]> {
    return super.get<any[]>(`/${instanceId}/sessions`).then(data => data.map(this.mapToSession));
  }

  async getDetail(instanceId: string): Promise<ProjectInstanceDetail> {
    return super
      .get<any>(`/${instanceId}/detail`)
      .then(
        data =>
          new ProjectInstanceDetail(
            this.mapToInstance(data.instance),
            data.stats.fileCount ?? 0,
            data.stats.totalSizeBytes ?? 0
          )
      );
  }

  async getSessionDetail(
    instanceId: string,
    sessionId: string
  ): Promise<ProjectInstanceSessionDetail> {
    return super
      .get<any>(`/${instanceId}/sessions/${sessionId}/detail`)
      .then(
        data =>
          new ProjectInstanceSessionDetail(
            this.mapToSession(data.session),
            data.environment ?? null
          )
      );
  }

  private mapToInstance = (data: any): ProjectInstance => {
    const sessions =
      Array.isArray(data.sessions) && data.sessions.length > 0
        ? data.sessions.map(this.mapToSession)
        : undefined;

    return new ProjectInstance(
      data.id,
      data.instanceName,
      data.projectId,
      data.status,
      data.createdAt,
      data.duration ?? 0,
      data.sessionCount || 0,
      data.activeSessionId,
      data.finishedAt ?? undefined,
      data.expiringAt ?? undefined,
      data.expiredAt ?? undefined,
      sessions
    );
  };

  private mapToSession(data: any): ProjectInstanceSession {
    return new ProjectInstanceSession(
      data.id,
      data.repositoryId,
      data.createdAt,
      data.duration ?? 0,
      data.finishedAt ?? undefined,
      data.isActive,
      data.failed ?? false
    );
  }
}
