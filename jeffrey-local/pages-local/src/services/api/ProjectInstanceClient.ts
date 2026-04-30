/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import ProjectInstance from '@/services/api/model/ProjectInstance';
import ProjectInstanceDetail from '@/services/api/model/ProjectInstanceDetail';
import ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession';
import ProjectInstanceSessionDetail from '@/services/api/model/ProjectInstanceSessionDetail';

export default class ProjectInstanceClient extends BasePlatformClient {
  constructor(serverId: string, workspaceId: string, projectId: string) {
    super(`/remote-servers/${serverId}/workspaces/${workspaceId}/projects/${projectId}/instances`);
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
    return super.get<any>(`/${instanceId}/detail`).then(data => new ProjectInstanceDetail(
        this.mapToInstance(data.instance),
        data.stats.fileCount ?? 0,
        data.stats.totalSizeBytes ?? 0
    ));
  }

  async getSessionDetail(instanceId: string, sessionId: string): Promise<ProjectInstanceSessionDetail> {
    return super.get<any>(`/${instanceId}/sessions/${sessionId}/detail`).then(data => new ProjectInstanceSessionDetail(
        this.mapToSession(data.session),
        data.environment ?? null
    ));
  }

  private mapToInstance = (data: any): ProjectInstance => {
    const sessions = Array.isArray(data.sessions) && data.sessions.length > 0
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
      data.isActive
    );
  }
}
