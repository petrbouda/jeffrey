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
import ThreadAnalysisReport from '@/services/api/model/ThreadAnalysisReport';
import InstanceDetail from '@/services/api/model/InstanceDetail';
import InstanceTreeResponse from '@/services/api/model/InstanceTreeResponse';
import { GCRootPath } from '@/services/api/model/GCRootPath';
import DominatorTreeResponse from '@/services/api/model/DominatorTreeResponse';
import CollectionAnalysisReport from '@/services/api/model/CollectionAnalysisReport';
import ClassInstancesResponse from '@/services/api/model/ClassInstancesResponse';
import LeakSuspectsReport from '@/services/api/model/LeakSuspectsReport';
import BiggestObjectsReport from '@/services/api/model/BiggestObjectsReport';
import HeapDumpConfig from '@/services/api/model/HeapDumpConfig';

export default class HeapDumpClient extends BaseProfileClient {

    constructor(profileId: string) {
        super(profileId, 'heap');
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

    public sanitize(): Promise<void> {
        return this.post<void>('/sanitize', {});
    }

    public initialize(compressedOops?: boolean): Promise<HeapSummary> {
        const params = compressedOops !== undefined ? `?compressedOops=${compressedOops}` : '';
        return this.post<HeapSummary>(`/initialize${params}`, {});
    }

    public getConfig(): Promise<HeapDumpConfig> {
        return this.get<HeapDumpConfig>('/config');
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
        return this.post<void>(`/string-analysis/run?topN=${topN}`, {});
    }

    public threadAnalysisExists(): Promise<boolean> {
        return this.get<boolean>('/thread-analysis/exists');
    }

    public getThreadAnalysis(): Promise<ThreadAnalysisReport> {
        return this.get<ThreadAnalysisReport>('/thread-analysis');
    }

    public runThreadAnalysis(): Promise<void> {
        return this.post<void>('/thread-analysis/run', {});
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

    // --- Path to GC Root ---

    public getPathToGCRoot(objectId: number, excludeWeak: boolean = true, maxPaths: number = 3): Promise<GCRootPath[]> {
        return this.get<GCRootPath[]>(`/instance/${objectId}/gc-root-path`, { excludeWeak, maxPaths });
    }

    // --- Dominator Tree ---

    public getDominatorTreeRoots(limit: number = 50): Promise<DominatorTreeResponse> {
        return this.get<DominatorTreeResponse>('/dominator-tree', { limit });
    }

    public getDominatorTreeChildren(objectId: number, limit: number = 50): Promise<DominatorTreeResponse> {
        return this.get<DominatorTreeResponse>(`/dominator-tree/${objectId}/children`, { limit });
    }

    // --- Collection Analysis ---

    public collectionAnalysisExists(): Promise<boolean> {
        return this.get<boolean>('/collection-analysis/exists');
    }

    public getCollectionAnalysis(): Promise<CollectionAnalysisReport> {
        return this.get<CollectionAnalysisReport>('/collection-analysis');
    }

    public runCollectionAnalysis(): Promise<void> {
        return this.post<void>('/collection-analysis/run', {});
    }

    // --- Class Instance Browser ---

    public getClassInstances(className: string, limit: number = 50, offset: number = 0, includeRetainedSize: boolean = false): Promise<ClassInstancesResponse> {
        return this.get<ClassInstancesResponse>('/class-instances', { className, limit, offset, includeRetainedSize });
    }

    // --- Leak Suspects ---

    public leakSuspectsExists(): Promise<boolean> {
        return this.get<boolean>('/leak-suspects/exists');
    }

    public getLeakSuspects(): Promise<LeakSuspectsReport> {
        return this.get<LeakSuspectsReport>('/leak-suspects');
    }

    public runLeakSuspects(): Promise<void> {
        return this.post<void>('/leak-suspects/run', {});
    }

    // --- Biggest Objects ---

    public biggestObjectsExists(): Promise<boolean> {
        return this.get<boolean>('/biggest-objects/exists');
    }

    public getBiggestObjects(): Promise<BiggestObjectsReport> {
        return this.get<BiggestObjectsReport>('/biggest-objects');
    }

    public runBiggestObjects(topN: number = 20): Promise<void> {
        return this.post<void>(`/biggest-objects/run?topN=${topN}`, {});
    }

}
