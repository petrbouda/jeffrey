/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis.ConsumerReportAnalysis;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis.CollectionHeapAnalysis;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis.ClassLoaderHeapAnalysis;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis.BiggestObjectsAnalysis;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDumpToolsDelegate;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.microscope.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.microscope.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.microscope.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.microscope.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.microscope.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.microscope.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.microscope.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.microscope.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.microscope.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.microscope.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.microscope.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.microscope.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.microscope.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.microscope.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.microscope.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.microscope.profile.heapdump.model.SortBy;
import cafe.jeffrey.microscope.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.microscope.profile.heapdump.view.HprofTag;
import cafe.jeffrey.microscope.profile.heapdump.view.SqlQueryResult;
import cafe.jeffrey.microscope.profile.management.manager.heapdump.HeapDumpManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapts a {@link HeapDumpManager} to the {@link HeapDumpToolsDelegate} contract the {@code heap_}
 * MCP tools read through, so those tools depend on the delegate rather than on the manager itself.
 */
public record HeapDumpManagerToolsDelegate(HeapDumpManager manager) implements HeapDumpToolsDelegate {

    /** The id is a {@code long}, so formatting it into the statement cannot inject anything. */
    private static final String OBJECT_EXISTS_SQL = "SELECT 1 FROM instance WHERE instance_id = %d LIMIT 1";
    private static final String ROOT_KIND_SQL = "SELECT root_kind FROM gc_root WHERE instance_id = %d LIMIT 1";
    private static final int ONE_ROW = 1;

    @Override
    public boolean objectExists(long objectId) {
        return !manager.executeSql(OBJECT_EXISTS_SQL.formatted(objectId), ONE_ROW).rows().isEmpty();
    }

    @Override
    public Optional<String> gcRootKind(long objectId) {
        List<List<String>> rows = manager.executeSql(ROOT_KIND_SQL.formatted(objectId), ONE_ROW).rows();
        if (rows.isEmpty() || rows.getFirst().getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(HprofTag.Sub.rootKindName(Integer.parseInt(rows.getFirst().getFirst().trim())));
    }

    @Override
    public Optional<GCRootPath> selfRootPath(long objectId) {
        return manager.getSelfRootPath(objectId);
    }

    @Override
    public Set<HeapReport> storedReports() {
        return HeapReport.storedIn(manager);
    }

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
        if (!manager.reportExists(new BiggestObjectsAnalysis())) {
            return null;
        }
        return manager.report(new BiggestObjectsAnalysis()).orElse(null);
    }

    @Override
    public LeakSuspectsReport getLeakSuspects() {
        if (!manager.reportExists(new LeakSuspectsAnalysis())) {
            return null;
        }
        return manager.report(new LeakSuspectsAnalysis()).orElse(null);
    }

    @Override
    public StringAnalysisReport getStringAnalysis() {
        if (!manager.reportExists(new StringHeapAnalysis())) {
            return null;
        }
        return manager.report(new StringHeapAnalysis()).orElse(null);
    }

    @Override
    public CollectionAnalysisReport getCollectionAnalysis() {
        if (!manager.reportExists(new CollectionHeapAnalysis())) {
            return null;
        }
        return manager.report(new CollectionHeapAnalysis()).orElse(null);
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
    public DominatorTreeResponse getDominatorTreeChildren(long objectId, int limit, int offset) {
        return manager.getDominatorTreeChildren(objectId, offset, limit);
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
    public SqlQueryResult executeSql(String sql, int maxRows) {
        return manager.executeSql(sql, maxRows);
    }

    @Override
    public OQLQueryResult executeQuery(OQLQueryRequest request) {
        return manager.executeQuery(request);
    }

    @Override
    public ClassLoaderReport getClassLoaderAnalysis() {
        if (!manager.reportExists(new ClassLoaderHeapAnalysis())) {
            return null;
        }
        return manager.report(new ClassLoaderHeapAnalysis()).orElse(null);
    }

    @Override
    public ConsumerReport getConsumerReport() {
        if (!manager.reportExists(new ConsumerReportAnalysis())) {
            return null;
        }
        return manager.report(new ConsumerReportAnalysis()).orElse(null);
    }
}
