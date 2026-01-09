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

package pbouda.jeffrey.profile.resources;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.profile.manager.HeapDumpManager;

import java.util.List;

/**
 * REST resource for heap dump analysis operations.
 */
public class HeapDumpResource {

    private final HeapDumpManager heapDumpManager;

    public HeapDumpResource(HeapDumpManager heapDumpManager) {
        this.heapDumpManager = heapDumpManager;
    }

    /**
     * Check if a heap dump exists for this profile.
     */
    @GET
    @Path("/exists")
    public boolean exists() {
        return heapDumpManager.heapDumpExists();
    }

    /**
     * Check if the heap dump cache is ready (already processed).
     */
    @GET
    @Path("/cache-ready")
    public boolean cacheReady() {
        return heapDumpManager.isCacheReady();
    }

    /**
     * Get heap summary statistics.
     */
    @GET
    @Path("/summary")
    public HeapSummary summary() {
        return heapDumpManager.getSummary();
    }

    /**
     * Get class histogram.
     *
     * @param topN   number of entries to return (default 100)
     * @param sortBy sort criteria: SIZE, COUNT, or CLASS_NAME (default SIZE)
     */
    @GET
    @Path("/histogram")
    public List<ClassHistogramEntry> histogram(
            @QueryParam("topN") @DefaultValue("100") int topN,
            @QueryParam("sortBy") @DefaultValue("SIZE") SortBy sortBy) {
        return heapDumpManager.getClassHistogram(topN, sortBy);
    }

    /**
     * Execute an OQL query.
     *
     * @param request query request with query string and pagination
     */
    @POST
    @Path("/query")
    public OQLQueryResult query(OQLQueryRequest request) {
        return heapDumpManager.executeQuery(request);
    }

    /**
     * Get thread information from the heap.
     */
    @GET
    @Path("/threads")
    public List<HeapThreadInfo> threads() {
        return heapDumpManager.getThreads();
    }

    /**
     * Get GC root summary.
     */
    @GET
    @Path("/gc-roots")
    public GCRootSummary gcRoots() {
        return heapDumpManager.getGCRootSummary();
    }

    /**
     * Unload the heap from memory cache.
     */
    @POST
    @Path("/unload")
    public void unload() {
        heapDumpManager.unloadHeap();
    }

    /**
     * Delete the heap dump cache (.nbcache directory).
     */
    @POST
    @Path("/delete-cache")
    public void deleteCache() {
        heapDumpManager.deleteCache();
    }

    /**
     * Delete all heap dump files (cache, heap dump, and compressed heap dump).
     */
    @POST
    @Path("/delete")
    public void deleteHeapDump() {
        heapDumpManager.deleteHeapDump();
    }
}
