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

export default class RemoteWorkspaceClient {

    private static baseUrl = GlobalVars.url + '/remote-workspaces';

    static async listRemote(remoteUrl: string): Promise<Workspace[]> {
        const content = {
            remoteUrl: remoteUrl
        };
        return axios.post<Workspace[]>(
            RemoteWorkspaceClient.baseUrl + "/list", content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static async createRemote(remoteUrl: string, workspaceIds: string[]): Promise<void> {
        const request = {
            remoteUrl: remoteUrl,
            workspaceIds: workspaceIds
        };
        return axios.post(RemoteWorkspaceClient.baseUrl + "/create", request, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(() => undefined);
    }
}
