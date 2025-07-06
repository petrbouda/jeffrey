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
import GCOverviewData from '@/services/profile/custom/gc/GCOverviewData';
import GCConfigurationData from '@/services/profile/custom/gc/GCConfigurationData';
import Serie from '@/services/timeseries/model/Serie';
import JdbcOverviewData from "@/services/profile/custom/jdbc/JdbcOverviewData.ts";
import GCTimeseriesType from "@/services/profile/custom/gc/GCTimeseriesType.ts";

export default class ProfileGCClient {
    private baseUrl: string;

    constructor(projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.url}/projects/${projectId}/profiles/${profileId}/gc`;
    }

    public getOverview(): Promise<GCOverviewData> {
        return axios.get<JdbcOverviewData>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public getTimeseries(timeseriesType: GCTimeseriesType): Promise<Serie> {
        return axios.get<JdbcOverviewData>(this.baseUrl + "/timeseries", {
            headers: {Accept: 'application/json'},
            params: {timeseriesType: timeseriesType},
        }).then(HttpUtils.RETURN_DATA)
    }

    public getConfiguration(): Promise<GCConfigurationData> {
        return axios.get<GCConfigurationData>(this.baseUrl + "/configuration", HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }
}
