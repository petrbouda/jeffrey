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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import pbouda.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import pbouda.jeffrey.profile.heapdump.model.GCRootPath;
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapDumpConfig;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.InstanceDetail;
import pbouda.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import pbouda.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import pbouda.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Manager for heap dump analysis operations.
 * Provides access to heap summary, class histogram, OQL queries, and other heap analysis features.
 */
public interface HeapDumpManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, HeapDumpManager> {
    }

    /**
     * Check if a heap dump file exists for this profile.
     *
     * @return true if heap dump is available
     */
    boolean heapDumpExists();

    /**
     * Check if the heap dump has already been processed (cache exists).
     * When cache exists, loading the heap is fast.
     *
     * @return true if cache is ready
     */
    boolean isCacheReady();

    /**
     * Get heap summary statistics.
     *
     * @return heap summary or null if heap dump not available
     */
    HeapSummary getSummary();

    /**
     * Get class histogram sorted by size.
     *
     * @param topN number of top entries to return
     * @return list of histogram entries
     */
    List<ClassHistogramEntry> getClassHistogram(int topN);

    /**
     * Get class histogram with custom sorting.
     *
     * @param topN   number of top entries to return
     * @param sortBy sort criteria
     * @return list of histogram entries
     */
    List<ClassHistogramEntry> getClassHistogram(int topN, SortBy sortBy);

    /**
     * Execute an OQL query.
     *
     * @param request query request with query string and pagination parameters
     * @return query result
     */
    OQLQueryResult executeQuery(OQLQueryRequest request);

    /**
     * Get thread information from the heap.
     *
     * @return list of thread info
     */
    List<HeapThreadInfo> getThreads();

    /**
     * Get thread information from the heap.
     *
     * @param includeRetainedSize whether to calculate retained size (expensive operation)
     * @return list of thread info
     */
    List<HeapThreadInfo> getThreads(boolean includeRetainedSize);

    /**
     * Get GC root summary.
     *
     * @return GC root summary
     */
    GCRootSummary getGCRootSummary();

    /**
     * Unload the heap from memory cache.
     * Call this to free memory when heap dump analysis is no longer needed.
     */
    void unloadHeap();

    /**
     * Delete the heap dump cache (.nbcache directory).
     * This forces reprocessing on next access.
     */
    void deleteCache();

    /**
     * Delete all heap dump files (cache, heap dump, and compressed heap dump).
     * This completely removes all heap dump data for this profile.
     */
    void deleteHeapDump();

    /**
     * Sanitize a corrupted heap dump file by creating a repaired copy.
     * The original file is preserved and a sanitized version is created alongside it.
     */
    void sanitizeHeapDump();

    /**
     * Upload a heap dump file.
     *
     * @param inputStream the input stream of the heap dump file
     * @param filename    the original filename (must end with .hprof or .hprof.gz)
     */
    void uploadHeapDump(InputStream inputStream, String filename);

    /**
     * Check if string analysis results exist for this profile.
     *
     * @return true if string-analysis.json exists
     */
    boolean stringAnalysisExists();

    /**
     * Get the pre-computed string analysis results.
     *
     * @return string analysis report, or null if not yet computed
     */
    StringAnalysisReport getStringAnalysis();

    /**
     * Run string analysis and save results to JSON file.
     *
     * @param topN number of top entries to include in the report
     */
    void runStringAnalysis(int topN);

    /**
     * Check if thread analysis results exist for this profile.
     *
     * @return true if thread-analysis.json exists
     */
    boolean threadAnalysisExists();

    /**
     * Get the pre-computed thread analysis results.
     *
     * @return thread analysis report, or null if not yet computed
     */
    ThreadAnalysisReport getThreadAnalysis();

    /**
     * Run thread analysis with retained heap calculation and save results to JSON file.
     */
    void runThreadAnalysis();

    /**
     * Get detailed information about an instance including all its fields.
     *
     * @param objectId            the object ID of the instance
     * @param includeRetainedSize whether to calculate retained size (expensive operation)
     * @return instance details or null if not found
     */
    InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize);

    /**
     * Get referrers of an instance (objects that reference this instance).
     *
     * @param objectId the object ID to find referrers for
     * @param limit    maximum number of referrers to return
     * @param offset   offset for pagination
     * @return tree response with referrers
     */
    InstanceTreeResponse getReferrers(long objectId, int limit, int offset);

    /**
     * Get reachables from an instance (objects that this instance references).
     *
     * @param objectId the object ID to find reachables for
     * @param limit    maximum number of reachables to return
     * @param offset   offset for pagination
     * @return tree response with reachables
     */
    InstanceTreeResponse getReachables(long objectId, int limit, int offset);

    // --- Heap Dump Config ---

    /**
     * Resolve compressed oops setting and store it to disk as heap-dump-config.json.
     * Detection priority: manual override -> JFR events -> heap inference.
     *
     * @param manualOverride if non-null, uses this value directly (source = MANUAL)
     * @return the resolved config
     */
    HeapDumpConfig resolveAndStoreCompressedOops(Boolean manualOverride);

    /**
     * Read the stored heap dump config from disk.
     *
     * @return the config, or empty if not yet resolved
     */
    Optional<HeapDumpConfig> getHeapDumpConfig();

    // --- Path to GC Root ---

    /**
     * Find the shortest reference chain(s) from GC roots to a given object.
     *
     * @param objectId        the target object ID
     * @param excludeWeakRefs whether to exclude weak references from paths
     * @param maxPaths        maximum number of paths to return
     * @return list of GC root paths to the target object
     */
    List<GCRootPath> getPathsToGCRoot(long objectId, boolean excludeWeakRefs, int maxPaths);

    // --- Dominator Tree ---

    /**
     * Get the top-level entries of the dominator tree (objects with the largest retained size).
     *
     * @param limit maximum number of entries to return
     * @return dominator tree response with top-level nodes
     */
    DominatorTreeResponse getDominatorTreeRoots(int limit);

    /**
     * Get children of a dominator tree node (objects retained by the given object).
     *
     * @param objectId the parent object ID
     * @param limit    maximum number of children to return
     * @return dominator tree response with children
     */
    DominatorTreeResponse getDominatorTreeChildren(long objectId, int limit);

    // --- Collection Analysis ---

    /**
     * Check if collection analysis results exist for this profile.
     */
    boolean collectionAnalysisExists();

    /**
     * Get the pre-computed collection analysis results.
     */
    CollectionAnalysisReport getCollectionAnalysis();

    /**
     * Run collection analysis and save results to JSON file.
     */
    void runCollectionAnalysis();

    // --- Class Instance Browser ---

    /**
     * Browse instances of a specific class with pagination.
     *
     * @param className           fully qualified class name
     * @param limit               maximum number of instances to return
     * @param offset              offset for pagination
     * @param includeRetainedSize whether to compute retained size per instance
     * @return paginated response with class instances
     */
    ClassInstancesResponse getClassInstances(String className, int limit, int offset, boolean includeRetainedSize);

    // --- Leak Suspects ---

    /**
     * Check if leak suspects analysis results exist for this profile.
     */
    boolean leakSuspectsExists();

    /**
     * Get the pre-computed leak suspects report.
     */
    LeakSuspectsReport getLeakSuspects();

    /**
     * Run leak suspects analysis and save results to JSON file.
     */
    void runLeakSuspects();

    // --- Biggest Objects ---

    /**
     * Check if biggest objects analysis results exist for this profile.
     */
    boolean biggestObjectsExists();

    /**
     * Get the pre-computed biggest objects report.
     */
    BiggestObjectsReport getBiggestObjects();

    /**
     * Run biggest objects analysis and save results to JSON file.
     *
     * @param topN number of biggest objects to include
     */
    void runBiggestObjects(int topN);

}
