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
import JITCompilationData from "@/services/compilation/model/JITCompilationData.ts";
import Serie from "@/services/timeseries/model/Serie.ts";
import JITLongCompilation from "@/services/compilation/model/JITLongCompilation.ts";

export default class ProfileCompilationClient {

    private baseUrl: string;

    constructor(projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.url}/projects/${projectId}/profiles/${profileId}/compilation`;
    }

    public getStatistics(): Promise<JITCompilationData> {
        return axios.get<JITCompilationData>(this.baseUrl + "/statistics", HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public getCompilations(): Promise<JITLongCompilation[]> {
        return axios.get<JITLongCompilation[]>(this.baseUrl + "/compilations", HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public getTimeseries(): Promise<Serie> {
        return axios.get<Serie>(this.baseUrl + "/timeseries", HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }
}
