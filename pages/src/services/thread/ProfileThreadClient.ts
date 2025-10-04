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
import ThreadResponse from "@/services/thread/model/ThreadResponse";
import ThreadStatisticsResponse from "@/services/thread/model/ThreadStatisticsResponse.ts";
import Serie from "@/services/timeseries/model/Serie.ts";

export default class ProfileThreadClient {

    private baseUrl: string;

    constructor(workspaceId: string, projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/thread`;
    }

    public list(): Promise<ThreadResponse> {
        return axios.get<ThreadResponse>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public statistics(): Promise<ThreadStatisticsResponse> {
        return axios.get<ThreadStatisticsResponse>(this.baseUrl + "/statistics", HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public timeseries(): Promise<Serie> {
        return axios.get<Serie>(this.baseUrl + "/timeseries", HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }
}
