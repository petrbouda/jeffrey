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
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.io.InputStream;
import java.util.List;
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
     * Upload a heap dump file.
     *
     * @param inputStream the input stream of the heap dump file
     * @param filename    the original filename (must end with .hprof or .hprof.gz)
     */
    void uploadHeapDump(InputStream inputStream, String filename);
}
