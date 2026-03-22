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

package pbouda.jeffrey.profile.ai.heapmcp.tools;

import pbouda.jeffrey.profile.heapdump.model.*;

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
}
