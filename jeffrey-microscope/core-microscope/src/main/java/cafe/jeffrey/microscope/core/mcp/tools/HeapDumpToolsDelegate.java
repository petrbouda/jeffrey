/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.heapdump.model.*;
import cafe.jeffrey.profile.heapdump.view.SqlQueryResult;

import java.util.List;

/**
 * Delegate interface for heap dump operations used by AI MCP tools.
 * Abstracts the HeapDumpManager so the tools module doesn't depend on profile-management.
 */
public interface HeapDumpToolsDelegate {

    HeapSummary getSummary();

    List<ClassHistogramEntry> getClassHistogram(int topN, SortBy sortBy);

    BiggestObjectsReport getBiggestObjects(int topN);

    LeakSuspectsReport getLeakSuspects();

    StringAnalysisReport getStringAnalysis();

    CollectionAnalysisReport getCollectionAnalysis();

    List<HeapThreadInfo> getThreads();

    GCRootSummary getGCRootSummary();

    ClassInstancesResponse getClassInstances(String className, int limit, int offset, boolean includeRetainedSize);

    InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize);

    DominatorTreeResponse getDominatorTreeRoots(int limit);

    DominatorTreeResponse getDominatorTreeChildren(long objectId, int limit);

    List<GCRootPath> getPathsToGCRoot(long objectId, boolean excludeWeakRefs, int maxPaths);

    InstanceTreeResponse getReferrers(long objectId, int limit, int offset);

    OQLQueryResult executeQuery(OQLQueryRequest request);

    /**
     * Runs a read-only SQL query against the heap-dump index, capped at {@code maxRows}.
     * <p>
     * Distinct from {@link #executeQuery(OQLQueryRequest)}, which parses OQL: OQL asks object
     * questions over the graph, this asks table questions of the index the graph is stored in.
     */
    SqlQueryResult executeSql(String sql, int maxRows);

    ClassLoaderReport getClassLoaderAnalysis();

    ConsumerReport getConsumerReport();
}
