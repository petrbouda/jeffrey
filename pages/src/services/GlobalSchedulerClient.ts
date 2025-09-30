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
import JobInfo from "@/services/model/JobInfo.ts";

export default class GlobalSchedulerClient {
    private static baseUrl: string = GlobalVars.internalUrl + '/scheduler'

    static create(jobType: string, params: Map<string, string>): Promise<JobInfo> {
        const content = {
            jobType: jobType,
            params: params,
        };

        return axios.post(GlobalSchedulerClient.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static all(): Promise<JobInfo[]> {
        return axios.get<JobInfo[]>(GlobalSchedulerClient.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static updateEnabled(jobId: string, enabled: boolean) {
        const content = {
            enabled: enabled,
        };

        return axios.put(GlobalSchedulerClient.baseUrl + '/' + jobId + '/enabled', content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static delete(jobId: string) {
        return axios.delete(GlobalSchedulerClient.baseUrl + '/' + jobId)
            .then(HttpUtils.RETURN_DATA);
    }
}
