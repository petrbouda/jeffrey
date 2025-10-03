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

import GlobalVars from '@/services/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import Project from '@/services/model/Project';

export default class WorkspaceProjectsClient {

    private baseUrl: string;

    constructor(workspaceId: string) {
        this.baseUrl = `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects`;
    }

    async list(): Promise<Project[]> {
        return axios.get<Project[]>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    async create(name: string, templateId?: string): Promise<Project> {
        const content: any = {name: name};
        if (templateId) {
            content.templateId = templateId;
        }
        return axios.post<Project>(this.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}