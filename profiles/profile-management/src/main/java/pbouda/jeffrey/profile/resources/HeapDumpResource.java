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

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.heapdump.model.*;
import pbouda.jeffrey.profile.manager.HeapDumpManager;

import java.io.InputStream;
import java.util.List;

/**
 * REST resource for heap dump analysis operations.
 */
public class HeapDumpResource {

    private final HeapDumpManager heapDumpManager;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;
    private final HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService;

    public HeapDumpResource(
            HeapDumpManager heapDumpManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor,
            HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService) {
        this.heapDumpManager = heapDumpManager;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
        this.heapDumpAnalysisAssistantService = heapDumpAnalysisAssistantService;
    }

    /**
     * Sub-resource for AI-powered OQL assistant.
     */
    @Path("/oql-assistant")
    public OqlAssistantResource oqlAssistantResource() {
        return new OqlAssistantResource(heapDumpManager, oqlAssistantService, heapDumpContextExtractor);
    }

    /**
     * Sub-resource for AI-powered heap dump analysis.
     */
    @Path("/ai-analysis")
    public HeapDumpAiAnalysisResource heapDumpAiAnalysisResource() {
        return new HeapDumpAiAnalysisResource(heapDumpManager, heapDumpAnalysisAssistantService);
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
     *
     * @param includeRetainedSize whether to calculate retained size (default false - expensive operation)
     */
    @GET
    @Path("/threads")
    public List<HeapThreadInfo> threads(
            @QueryParam("includeRetained") @DefaultValue("false") boolean includeRetainedSize) {
        return heapDumpManager.getThreads(includeRetainedSize);
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

    /**
     * Upload a heap dump file.
     *
     * @param fileInputStream the input stream of the uploaded file
     * @param cdh             content disposition header with filename
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void uploadHeapDump(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition cdh) {
        heapDumpManager.uploadHeapDump(fileInputStream, cdh.getFileName());
    }

    /**
     * Check if string analysis results exist.
     */
    @GET
    @Path("/string-analysis/exists")
    public boolean stringAnalysisExists() {
        return heapDumpManager.stringAnalysisExists();
    }

    /**
     * Get pre-computed string analysis results.
     */
    @GET
    @Path("/string-analysis")
    public StringAnalysisReport getStringAnalysis() {
        return heapDumpManager.getStringAnalysis();
    }

    /**
     * Run string analysis and save results to JSON file.
     *
     * @param topN number of top entries to include in each list (default 100)
     */
    @POST
    @Path("/string-analysis/run")
    public void runStringAnalysis(
            @QueryParam("topN") @DefaultValue("100") int topN) {
        heapDumpManager.runStringAnalysis(topN);
    }

    /**
     * Check if thread analysis results exist.
     */
    @GET
    @Path("/thread-analysis/exists")
    public boolean threadAnalysisExists() {
        return heapDumpManager.threadAnalysisExists();
    }

    /**
     * Get pre-computed thread analysis results with retained heap sizes.
     */
    @GET
    @Path("/thread-analysis")
    public ThreadAnalysisReport getThreadAnalysis() {
        return heapDumpManager.getThreadAnalysis();
    }

    /**
     * Run thread analysis with retained heap calculation and save results to JSON file.
     * This is an expensive operation that computes retained heap for all threads.
     */
    @POST
    @Path("/thread-analysis/run")
    public void runThreadAnalysis() {
        heapDumpManager.runThreadAnalysis();
    }

    /**
     * Get detailed information about an instance including all its fields.
     *
     * @param objectId            the object ID of the instance
     * @param includeRetainedSize whether to calculate retained size (default false)
     */
    @GET
    @Path("/instance/{objectId}")
    public InstanceDetail getInstanceDetail(
            @PathParam("objectId") long objectId,
            @QueryParam("includeRetained") @DefaultValue("false") boolean includeRetainedSize) {
        return heapDumpManager.getInstanceDetail(objectId, includeRetainedSize);
    }

    /**
     * Get referrers of an instance (objects that reference this instance).
     *
     * @param objectId the object ID to find referrers for
     * @param limit    maximum number of referrers to return (default 50)
     * @param offset   offset for pagination (default 0)
     */
    @GET
    @Path("/instance/{objectId}/referrers")
    public InstanceTreeResponse getReferrers(
            @PathParam("objectId") long objectId,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        return heapDumpManager.getReferrers(objectId, limit, offset);
    }

    /**
     * Get reachables from an instance (objects that this instance references).
     *
     * @param objectId the object ID to find reachables for
     * @param limit    maximum number of reachables to return (default 50)
     * @param offset   offset for pagination (default 0)
     */
    @GET
    @Path("/instance/{objectId}/reachables")
    public InstanceTreeResponse getReachables(
            @PathParam("objectId") long objectId,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        return heapDumpManager.getReachables(objectId, limit, offset);
    }

    // --- Path to GC Root ---

    @GET
    @Path("/instance/{objectId}/gc-root-path")
    public List<GCRootPath> getPathToGCRoot(
            @PathParam("objectId") long objectId,
            @QueryParam("excludeWeak") @DefaultValue("true") boolean excludeWeakRefs,
            @QueryParam("maxPaths") @DefaultValue("3") int maxPaths) {
        return heapDumpManager.getPathsToGCRoot(objectId, excludeWeakRefs, maxPaths);
    }

    // --- Heap Dump Config ---

    /**
     * Initialize heap dump: resolve compressed oops setting and build indexes.
     * If compressedOops param is absent, auto-detects via JFR events or heap inference.
     *
     * @param compressedOops optional manual override for compressed oops
     * @return heap summary after initialization
     */
    @POST
    @Path("/initialize")
    public HeapSummary initialize(
            @QueryParam("compressedOops") Boolean compressedOops) {
        heapDumpManager.resolveAndStoreCompressedOops(compressedOops);
        return heapDumpManager.getSummary();
    }

    /**
     * Get the stored heap dump config (compressed oops setting and source).
     */
    @GET
    @Path("/config")
    public HeapDumpConfig getConfig() {
        return heapDumpManager.getHeapDumpConfig().orElse(null);
    }

    // --- Dominator Tree ---

    @GET
    @Path("/dominator-tree")
    public DominatorTreeResponse getDominatorTreeRoots(
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return heapDumpManager.getDominatorTreeRoots(limit);
    }

    @GET
    @Path("/dominator-tree/{objectId}/children")
    public DominatorTreeResponse getDominatorTreeChildren(
            @PathParam("objectId") long objectId,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return heapDumpManager.getDominatorTreeChildren(objectId, limit);
    }

    // --- Collection Analysis ---

    @GET
    @Path("/collection-analysis/exists")
    public boolean collectionAnalysisExists() {
        return heapDumpManager.collectionAnalysisExists();
    }

    @GET
    @Path("/collection-analysis")
    public CollectionAnalysisReport getCollectionAnalysis() {
        return heapDumpManager.getCollectionAnalysis();
    }

    @POST
    @Path("/collection-analysis/run")
    public void runCollectionAnalysis() {
        heapDumpManager.runCollectionAnalysis();
    }

    // --- Class Instance Browser ---

    @GET
    @Path("/class-instances")
    public ClassInstancesResponse getClassInstances(
            @QueryParam("className") String className,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("includeRetainedSize") @DefaultValue("false") boolean includeRetainedSize) {
        return heapDumpManager.getClassInstances(className, limit, offset, includeRetainedSize);
    }

    // --- Leak Suspects ---

    @GET
    @Path("/leak-suspects/exists")
    public boolean leakSuspectsExists() {
        return heapDumpManager.leakSuspectsExists();
    }

    @GET
    @Path("/leak-suspects")
    public LeakSuspectsReport getLeakSuspects() {
        return heapDumpManager.getLeakSuspects();
    }

    @POST
    @Path("/leak-suspects/run")
    public void runLeakSuspects() {
        heapDumpManager.runLeakSuspects();
    }

}
