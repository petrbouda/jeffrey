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
import Workspace from '@/services/workspace/model/Workspace';
import CreateWorkspaceRequest from '@/services/workspace/model/CreateWorkspaceRequest';
import WorkspaceEvent from '@/services/model/WorkspaceEvent';

export default class WorkspaceClient {

    private static baseUrl = GlobalVars.url + '/workspaces';

    /**
     * Get all workspaces
     * GET /api/workspaces
     */
    static async list(excludeRemote: boolean = false): Promise<Workspace[]> {
        return axios.get<Workspace[]>(WorkspaceClient.baseUrl, {
            ...HttpUtils.JSON_ACCEPT_HEADER,
            params: {excludeRemote: excludeRemote}
        }).then(HttpUtils.RETURN_DATA);
    }

    /**
     * Create a new workspace
     * POST /api/workspaces
     */
    static async create(request: CreateWorkspaceRequest): Promise<Workspace> {
        return axios.post<Workspace>(WorkspaceClient.baseUrl, request, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    /**
     * Delete a workspace by ID
     * DELETE /api/workspaces/{workspaceId}
     */
    static async delete(workspaceId: string): Promise<void> {
        const url = `${WorkspaceClient.baseUrl}/${workspaceId}`;
        return axios.delete(url, HttpUtils.JSON_ACCEPT_HEADER)
            .then(() => undefined);
    }

    /**
     * Get events for a specific workspace
     * GET /api/workspaces/{workspaceId}/events
     */
    static async getEvents(workspaceId: string): Promise<WorkspaceEvent[]> {
        const url = `${WorkspaceClient.baseUrl}/${workspaceId}/events`;
        return axios.get<WorkspaceEvent[]>(url, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
