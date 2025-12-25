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

import GlobalVars from '@/services/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import SettingsResponse from "@/services/api/model/SettingsResponse.ts";

export default class ProjectSettingsClient {
    private baseUrl: string;

    constructor(workspaceId: string, projectId: string) {
        this.baseUrl = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/settings'
    }

    updateName(name: string): Promise<void> {
        const content = {
            name: name,
        };

        return axios.post<void>(this.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    get(): Promise<SettingsResponse> {
        return axios.get<SettingsResponse>(this.baseUrl)
            .then(HttpUtils.RETURN_DATA);
    }
}
