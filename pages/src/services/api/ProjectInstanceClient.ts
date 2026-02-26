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

import axios from 'axios';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession';
import GlobalVars from '@/services/GlobalVars';
import HttpUtils from '@/services/HttpUtils';

export default class ProjectInstanceClient {

    private readonly baseUrl: string;

    constructor(workspaceId: string, projectId: string) {
        this.baseUrl = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/instances';
    }

    async list(): Promise<ProjectInstance[]> {
        return axios.get<ProjectInstance[]>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
            .then(data => data.map(this.mapToInstance));
    }

    async get(instanceId: string): Promise<ProjectInstance | undefined> {
        return axios.get<ProjectInstance>(this.baseUrl + '/' + instanceId, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
            .then(data => this.mapToInstance(data))
            .catch(() => undefined);
    }

    async getSessions(instanceId: string): Promise<ProjectInstanceSession[]> {
        return axios.get<ProjectInstanceSession[]>(this.baseUrl + '/' + instanceId + '/sessions', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
            .then(data => data.map(this.mapToSession));
    }

    async listActive(): Promise<ProjectInstance[]> {
        const instances = await this.list();
        return instances.filter(i => i.status === 'ACTIVE');
    }

    async listFinished(): Promise<ProjectInstance[]> {
        const instances = await this.list();
        return instances.filter(i => i.status === 'FINISHED');
    }

    private mapToInstance(data: any): ProjectInstance {
        return new ProjectInstance(
            data.id,
            data.hostname,
            data.projectId || '',
            data.status,
            data.startedAt ?? Date.now(),
            data.sessionCount || 0,
            data.activeSessionId,
            data.finishedAt ?? undefined
        );
    }

    private mapToSession(data: any): ProjectInstanceSession {
        return new ProjectInstanceSession(
            data.id,
            data.repositoryId,
            data.startedAt ?? Date.now(),
            data.finishedAt ?? undefined,
            data.isActive
        );
    }
}
