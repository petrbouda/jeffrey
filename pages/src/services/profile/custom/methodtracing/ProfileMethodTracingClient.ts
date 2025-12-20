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
import MethodTracingOverviewData from '@/services/profile/custom/methodtracing/MethodTracingOverviewData';
import MethodTracingSlowestData from '@/services/profile/custom/methodtracing/MethodTracingSlowestData';
import MethodTracingCumulatedData from '@/services/profile/custom/methodtracing/MethodTracingCumulatedData';

export default class ProfileMethodTracingClient {
    private baseUrl: string;

    constructor(workspaceId: string, projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/method-tracing`;
    }

    public getOverview(): Promise<MethodTracingOverviewData> {
        return axios
            .get<MethodTracingOverviewData>(`${this.baseUrl}/overview`, {
                headers: { Accept: 'application/json' },
            })
            .then(HttpUtils.RETURN_DATA);
    }

    public getSlowest(): Promise<MethodTracingSlowestData> {
        return axios
            .get<MethodTracingSlowestData>(`${this.baseUrl}/slowest`, {
                headers: { Accept: 'application/json' },
            })
            .then(HttpUtils.RETURN_DATA);
    }

    public getCumulated(mode: 'method' | 'class'): Promise<MethodTracingCumulatedData> {
        return axios
            .get<MethodTracingCumulatedData>(`${this.baseUrl}/cumulated`, {
                params: { mode },
                headers: { Accept: 'application/json' },
            })
            .then(HttpUtils.RETURN_DATA);
    }
}
