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
import HttpOverviewData from "@/services/profile/custom/http/HttpOverviewData.ts";
import HttpSingleUriData from "@/services/profile/custom/http/HttpSingleUriData.ts";

export default class ProfileHttpClient {

    private baseUrl: string;
    private mode: string;

    constructor(mode: 'client' | 'server', projectId: string, profileId: string) {
        this.mode = mode;
        this.baseUrl = `${GlobalVars.url}/projects/${projectId}/profiles/${profileId}/http/overview`;
    }

    public getOverview(): Promise<HttpOverviewData> {
        return axios.get<HttpOverviewData>(this.baseUrl, {
            headers: {Accept: 'application/json'},
            params: {mode: this.mode},
        }).then(HttpUtils.RETURN_DATA)
    }

    public getOverviewUri(uri: string | null): Promise<HttpSingleUriData> {
        return axios.get<HttpOverviewData>(this.baseUrl + "/single", {
            headers: {Accept: 'application/json'},
            params: {uri: uri, mode: this.mode},
        }).then(HttpUtils.RETURN_DATA)
    }
}
