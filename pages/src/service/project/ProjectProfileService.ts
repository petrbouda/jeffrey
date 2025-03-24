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

import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';
import ProfileInfo from "@/service/project/model/ProfileInfo";

export default class ProjectProfileService {

    private baseUrl: string;

    constructor(projectId: string) {
        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles'
    }

    get(profileId: string): Promise<ProfileInfo> {
        return axios.get<ProfileInfo>(this.baseUrl + '/' + profileId, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    list(): Promise<ProfileInfo[]> {
        return axios.get<ProfileInfo[]>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    create(recordingPath: string): Promise<ProfileInfo> {
        const content = {
            recordingPath: recordingPath
        };

        return axios.post<ProfileInfo>(this.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete(profileId: string): Promise<void> {
        return axios.delete(this.baseUrl + '/' + profileId)
            .then(HttpUtils.RETURN_DATA);
    }
}
