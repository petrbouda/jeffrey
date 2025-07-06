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
import HeapMemoryOverviewData from './HeapMemoryOverviewData';
import MemoryPoolTimelines from './MemoryPoolTimelines';
import AllocationStatistics from './AllocationStatistics';

export default class ProfileHeapMemoryClient {
    private baseUrl: string;

    constructor(projectId: string, profileId: string) {
        this.baseUrl = `${GlobalVars.url}/projects/${projectId}/profiles/${profileId}/heap-memory`;
    }

    public getOverview(): Promise<HeapMemoryOverviewData> {
        return axios.get<HeapMemoryOverviewData>(`${this.baseUrl}/overview`, {
            headers: {Accept: 'application/json'}
        }).then(HttpUtils.RETURN_DATA);
    }

    public getPoolTimelines(timeRange: string): Promise<MemoryPoolTimelines> {
        return axios.get<MemoryPoolTimelines>(`${this.baseUrl}/pool-timelines`, {
            headers: {Accept: 'application/json'},
            params: {timeRange}
        }).then(HttpUtils.RETURN_DATA);
    }

    public getAllocationStatistics(timeRange: string): Promise<AllocationStatistics> {
        return axios.get<AllocationStatistics>(`${this.baseUrl}/allocation-statistics`, {
            headers: {Accept: 'application/json'},
            params: {timeRange}
        }).then(HttpUtils.RETURN_DATA);
    }
}