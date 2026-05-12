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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisResponse;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpChatMessage;
import cafe.jeffrey.profile.ai.duckdb.heapdump.service.HeapDumpAnalysisAssistantService;
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

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap/ai-analysis")
public class HeapDumpAiAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpAiAnalysisController.class);

    private final ProfileManagerResolver resolver;
    private final HeapDumpAnalysisAssistantService assistantService;

    public HeapDumpAiAnalysisController(
            ProfileManagerResolver resolver,
            HeapDumpAnalysisAssistantService assistantService) {
        this.resolver = resolver;
        this.assistantService = assistantService;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        LOG.debug("Checking heap dump AI analysis status");
        return new StatusResponse(
                assistantService.isAvailable(),
                assistantService.getProviderName(),
                assistantService.getModelName());
    }

    @PostMapping("/chat")
    public HeapDumpAnalysisResponse chat(
            @PathVariable("profileId") String profileId,
            @RequestBody ChatRequest request) {
        LOG.debug("Heap dump AI analysis chat request");
        HeapDumpAnalysisRequest analysisRequest = new HeapDumpAnalysisRequest(
                request.message(),
                request.history() != null ? request.history() : List.of());
        HeapDumpToolsDelegate delegate = new HeapDumpManagerDelegate(resolver.resolve(profileId).heapDumpManager());
        return assistantService.analyze(delegate, analysisRequest);
    }

    public record StatusResponse(boolean available, String provider, String model) {
    }

    public record ChatRequest(String message, List<HeapDumpChatMessage> history) {
    }

    private record HeapDumpManagerDelegate(HeapDumpManager manager) implements HeapDumpToolsDelegate {

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
}
