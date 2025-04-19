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
import Profile from "@/services/model/Profile";

export default class ProjectProfileClient {

    private baseUrl: string;

    constructor(projectId: string) {
        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles'
    }

    async get(profileId: string): Promise<Profile> {
        return axios.get<Profile>(this.baseUrl + '/' + profileId, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    async list(): Promise<Profile[]> {
        return axios.get<Profile[]>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    async create(recordingId: string): Promise<Profile> {
        const content = {
            recordingId: recordingId
        };

        return axios.post<Profile>(this.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    async delete(profileId: string): Promise<void> {
        return axios.delete(this.baseUrl + '/' + profileId)
            .then(HttpUtils.RETURN_DATA);
    }
}
