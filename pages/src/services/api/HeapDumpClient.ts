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

import axios from 'axios';
import BaseProfileClient from '@/services/api/BaseProfileClient';
import HttpUtils from '@/services/HttpUtils';
import HeapSummary from '@/services/api/model/HeapSummary';
import ClassHistogramEntry from '@/services/api/model/ClassHistogramEntry';
import OQLQueryResult from '@/services/api/model/OQLQueryResult';
import GCRootSummary from '@/services/api/model/GCRootSummary';
import HeapThreadInfo from '@/services/api/model/HeapThreadInfo';
import StringAnalysisReport from '@/services/api/model/StringAnalysisReport';
import InstanceDetail from '@/services/api/model/InstanceDetail';
import InstanceTreeResponse from '@/services/api/model/InstanceTreeResponse';

export default class HeapDumpClient extends BaseProfileClient {

    constructor(workspaceId: string, projectId: string, profileId: string) {
        super(workspaceId, projectId, profileId, 'heap');
    }

    public exists(): Promise<boolean> {
        return this.get<boolean>('/exists');
    }

    public isCacheReady(): Promise<boolean> {
        return this.get<boolean>('/cache-ready');
    }

    public getSummary(): Promise<HeapSummary> {
        return this.get<HeapSummary>('/summary');
    }

    public getHistogram(topN: number = 100, sortBy: string = 'SIZE'): Promise<ClassHistogramEntry[]> {
        return this.get<ClassHistogramEntry[]>('/histogram', { topN, sortBy });
    }

    public executeQuery(query: string, limit: number = 100, offset: number = 0, includeRetainedSize: boolean = true): Promise<OQLQueryResult> {
        return this.post<OQLQueryResult>('/query', { query, limit, offset, includeRetainedSize });
    }

    public getThreads(): Promise<HeapThreadInfo[]> {
        return this.get<HeapThreadInfo[]>('/threads');
    }

    public getGCRoots(): Promise<GCRootSummary> {
        return this.get<GCRootSummary>('/gc-roots');
    }

    public unload(): Promise<void> {
        return this.post<void>('/unload', {});
    }

    public deleteCache(): Promise<void> {
        return this.post<void>('/delete-cache', {});
    }

    public deleteHeapDump(): Promise<void> {
        return this.post<void>('/delete', {});
    }

    public uploadHeapDump(file: File): Promise<void> {
        const formData = new FormData();
        formData.append("file", file, file.name);
        return axios.post(`${this.baseUrl}/upload`, formData, HttpUtils.MULTIPART_FORM_DATA_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    public stringAnalysisExists(): Promise<boolean> {
        return this.get<boolean>('/string-analysis/exists');
    }

    public getStringAnalysis(): Promise<StringAnalysisReport> {
        return this.get<StringAnalysisReport>('/string-analysis');
    }

    public runStringAnalysis(topN: number = 100): Promise<void> {
        return this.post<void>('/string-analysis/run', {}, { topN });
    }

    public deleteStringAnalysis(): Promise<void> {
        return this.post<void>('/string-analysis/delete', {});
    }

    public getInstanceDetail(objectId: number, includeRetained: boolean = false): Promise<InstanceDetail> {
        return this.get<InstanceDetail>(`/instance/${objectId}`, { includeRetained });
    }

    public getReferrers(objectId: number, limit: number = 50, offset: number = 0): Promise<InstanceTreeResponse> {
        return this.get<InstanceTreeResponse>(`/instance/${objectId}/referrers`, { limit, offset });
    }

    public getReachables(objectId: number, limit: number = 50, offset: number = 0): Promise<InstanceTreeResponse> {
        return this.get<InstanceTreeResponse>(`/instance/${objectId}/reachables`, { limit, offset });
    }
}
