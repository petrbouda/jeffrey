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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpAnalysisRequest;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpAnalysisResponse;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpChatMessage;
import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.heapmcp.tools.HeapDumpToolsDelegate;
import pbouda.jeffrey.profile.manager.HeapDumpManager;

import java.util.List;

/**
 * REST resource for AI-powered heap dump analysis.
 * Provides endpoints for chat-based analysis of heap dumps using MCP tools.
 */
public class HeapDumpAiAnalysisResource {

    private final HeapDumpManager heapDumpManager;
    private final HeapDumpAnalysisAssistantService assistantService;

    public HeapDumpAiAnalysisResource(
            HeapDumpManager heapDumpManager,
            HeapDumpAnalysisAssistantService assistantService) {
        this.heapDumpManager = heapDumpManager;
        this.assistantService = assistantService;
    }

    @GET
    @Path("/status")
    public StatusResponse status() {
        return new StatusResponse(
                assistantService.isAvailable(),
                assistantService.getProviderName(),
                assistantService.getModelName()
        );
    }

    @POST
    @Path("/chat")
    public HeapDumpAnalysisResponse chat(ChatRequest request) {
        HeapDumpAnalysisRequest analysisRequest = new HeapDumpAnalysisRequest(
                request.message(),
                request.history() != null ? request.history() : List.of()
        );

        HeapDumpToolsDelegate delegate = new HeapDumpManagerDelegate(heapDumpManager);
        return assistantService.analyze(delegate, analysisRequest);
    }

    public record StatusResponse(
            boolean available,
            String provider,
            String model
    ) {
    }

    public record ChatRequest(
            String message,
            List<HeapDumpChatMessage> history
    ) {
    }

    /**
     * Adapter that implements HeapDumpToolsDelegate by delegating to HeapDumpManager.
     */
    private record HeapDumpManagerDelegate(HeapDumpManager manager) implements HeapDumpToolsDelegate {

        @Override
        public pbouda.jeffrey.profile.heapdump.model.HeapSummary getSummary() {
            return manager.getSummary();
        }

        @Override
        public List<pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry> getClassHistogram(int topN, pbouda.jeffrey.profile.heapdump.model.SortBy sortBy) {
            return manager.getClassHistogram(topN, sortBy);
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.BiggestObjectsReport getBiggestObjects(int topN) {
            if (!manager.biggestObjectsExists()) {
                return null;
            }
            return manager.getBiggestObjects();
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.LeakSuspectsReport getLeakSuspects() {
            if (!manager.leakSuspectsExists()) {
                return null;
            }
            return manager.getLeakSuspects();
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport getStringAnalysis() {
            if (!manager.stringAnalysisExists()) {
                return null;
            }
            return manager.getStringAnalysis();
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.CollectionAnalysisReport getCollectionAnalysis() {
            if (!manager.collectionAnalysisExists()) {
                return null;
            }
            return manager.getCollectionAnalysis();
        }

        @Override
        public List<pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo> getThreads() {
            return manager.getThreads();
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.GCRootSummary getGCRootSummary() {
            return manager.getGCRootSummary();
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.ClassInstancesResponse getClassInstances(String className, int limit, int offset, boolean includeRetainedSize) {
            return manager.getClassInstances(className, limit, offset, includeRetainedSize);
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize) {
            return manager.getInstanceDetail(objectId, includeRetainedSize);
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.DominatorTreeResponse getDominatorTreeRoots(int limit) {
            return manager.getDominatorTreeRoots(limit);
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.DominatorTreeResponse getDominatorTreeChildren(long objectId, int limit) {
            return manager.getDominatorTreeChildren(objectId, limit);
        }

        @Override
        public List<pbouda.jeffrey.profile.heapdump.model.GCRootPath> getPathsToGCRoot(long objectId, boolean excludeWeakRefs, int maxPaths) {
            return manager.getPathsToGCRoot(objectId, excludeWeakRefs, maxPaths);
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.InstanceTreeResponse getReferrers(long objectId, int limit, int offset) {
            return manager.getReferrers(objectId, limit, offset);
        }

        @Override
        public pbouda.jeffrey.profile.heapdump.model.OQLQueryResult executeQuery(pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest request) {
            return manager.executeQuery(request);
        }
    }
}
