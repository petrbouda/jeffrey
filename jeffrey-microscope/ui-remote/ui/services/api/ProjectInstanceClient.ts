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
import ProjectInstance from '@remote/services/api/model/ProjectInstance';
import RecordingSession from '@remote/services/api/model/RecordingSession';

const NOT_FOUND = 404;

export interface InstanceWithSessions {
  instance: ProjectInstance;
  /** A page of the instance's sessions, newest first, each with its files. */
  sessions: RecordingSession[];
  /** Whether sessions older than the page's are left to read. */
  hasOlder: boolean;
}

export default class ProjectInstanceClient extends BasePlatformClient {
  constructor(workspaceId: string, projectId: string) {
    super(`/remote/workspaces/${workspaceId}/projects/${projectId}/instances`);
  }

  async list(): Promise<ProjectInstance[]> {
    return super.get<any[]>('').then(data => data.map(this.mapToInstance));
  }

  /**
   * One instance with a page of its sessions, newest first, each with its files — what the
   * instance's page shows, opening a session included, in one call. `before` names the session
   * the previous page ended on. Undefined when the remote no longer has it.
   */
  async find(instanceId: string, before?: string): Promise<InstanceWithSessions | undefined> {
    const params = before ? { before } : undefined;
    return super
      .get<any>(`/${instanceId}`, params)
      .then(data => ({
        instance: this.mapToInstance(data.instance),
        sessions: data.sessions as RecordingSession[],
        hasOlder: data.hasOlder === true
      }))
      .catch(error => {
        if (error.response?.status === NOT_FOUND) {
          return undefined;
        }
        throw error;
      });
  }

  private mapToInstance = (data: any): ProjectInstance => {
    return new ProjectInstance(
      data.id,
      data.instanceName,
      data.createdAt,
      data.lastDataAt ?? null,
      data.sessionCount || 0
    );
  };
}
