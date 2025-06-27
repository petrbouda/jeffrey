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
import Serie from "@/services/timeseries/model/Serie.ts";
import PoolData from "@/services/profile/custom/jdbc/model/PoolData.ts";

export default class ProfileJdbcClient {

    private baseUrl: string;

    constructor(projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.url}/projects/${projectId}/profiles/${profileId}/jdbc/pool`;
    }

    public getPoolData(): Promise<PoolData[]> {
        return axios.get<PoolData[]>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public getTimeseries(poolName: string, eventType: string): Promise<Serie> {
        const requestBody = {
            poolName: poolName,
            eventType: eventType
        }

        return axios.post<Serie>(this.baseUrl + "/timeseries", requestBody, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }
}
