/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpToolsDelegate;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.manager.HeapDumpManager;

import java.util.List;

/**
 * Adapts a {@link HeapDumpManager} to the {@link HeapDumpToolsDelegate} contract consumed by the heap
 * dump AI tools. Shared by the heap dump AI analysis controller (Spring AI path) and the MCP server
 * (Claude Code path) so both expose identical heap dump capabilities.
 */
public record HeapDumpManagerToolsDelegate(HeapDumpManager manager) implements HeapDumpToolsDelegate {

    @Override
    public HeapSummary getSummary() {
        return manager.getSummary();
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN, SortBy sortBy) {
        return manager.getClassHistogram(topN, sortBy);
    }

    @Override
    public BiggestObjectsReport getBiggestObjects(int topN) {
        if (!manager.biggestObjectsExists()) {
            return null;
        }
        return manager.getBiggestObjects();
    }

    @Override
    public LeakSuspectsReport getLeakSuspects() {
        if (!manager.leakSuspectsExists()) {
            return null;
        }
        return manager.getLeakSuspects();
    }

    @Override
    public StringAnalysisReport getStringAnalysis() {
        if (!manager.stringAnalysisExists()) {
            return null;
        }
        return manager.getStringAnalysis();
    }

    @Override
    public CollectionAnalysisReport getCollectionAnalysis() {
        if (!manager.collectionAnalysisExists()) {
            return null;
        }
        return manager.getCollectionAnalysis();
    }

    @Override
    public List<HeapThreadInfo> getThreads() {
        return manager.getThreads();
    }

    @Override
    public GCRootSummary getGCRootSummary() {
        return manager.getGCRootSummary();
    }

    @Override
    public ClassInstancesResponse getClassInstances(String className, int limit, int offset, boolean includeRetainedSize) {
        return manager.getClassInstances(className, limit, offset, includeRetainedSize);
    }

    @Override
    public InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize) {
        return manager.getInstanceDetail(objectId, includeRetainedSize);
    }

    @Override
    public DominatorTreeResponse getDominatorTreeRoots(int limit) {
        return manager.getDominatorTreeRoots(limit);
    }

    @Override
    public DominatorTreeResponse getDominatorTreeChildren(long objectId, int limit) {
        return manager.getDominatorTreeChildren(objectId, 0, limit);
    }

    @Override
    public List<GCRootPath> getPathsToGCRoot(long objectId, boolean excludeWeakRefs, int maxPaths) {
        return manager.getPathsToGCRoot(objectId, excludeWeakRefs, maxPaths);
    }

    @Override
    public InstanceTreeResponse getReferrers(long objectId, int limit, int offset) {
        return manager.getReferrers(objectId, limit, offset);
    }

    @Override
    public OQLQueryResult executeQuery(OQLQueryRequest request) {
        return manager.executeQuery(request);
    }

    @Override
    public ClassLoaderReport getClassLoaderAnalysis() {
        if (!manager.classLoaderAnalysisExists()) {
            return null;
        }
        return manager.getClassLoaderAnalysis();
    }

    @Override
    public ConsumerReport getConsumerReport() {
        if (!manager.consumerReportExists()) {
            return null;
        }
        return manager.getConsumerReport();
    }
}
