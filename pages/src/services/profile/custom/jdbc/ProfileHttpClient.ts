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

export default class ProfileHttpClient {

    private baseUrl: string;

    constructor(projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.url}/projects/${projectId}/profiles/${profileId}/http/overview`;
    }

    public getOverview(): Promise<HttpOverviewData> {
        return axios.get<HttpOverviewData>(this.baseUrl, {
            headers: {Accept: 'application/json'},
            params: {uri: ''},
        })
            .then(HttpUtils.RETURN_DATA)
    }

    public getOverviewWithUri(uri: string | null): Promise<HttpOverviewData> {
        if (uri === null || uri === '') {
            return this.getOverview();
        }

        return axios.get<HttpOverviewData>(this.baseUrl, {
            headers: {Accept: 'application/json'},
            params: {uri: uri},
        })
            .then(HttpUtils.RETURN_DATA)
    }
}
