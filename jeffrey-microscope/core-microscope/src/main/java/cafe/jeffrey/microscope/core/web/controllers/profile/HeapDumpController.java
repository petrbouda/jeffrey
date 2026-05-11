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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.DuplicateObjectsReport;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapDumpConfig;
import cafe.jeffrey.profile.heapdump.model.InitPipelineResult;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ThreadStackFrame;
import cafe.jeffrey.profile.manager.HeapDumpManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap")
public class HeapDumpController {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpController.class);

    private final ProfileManagerResolver resolver;

    public HeapDumpController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/exists")
    public boolean exists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).heapDumpExists();
    }

    @GetMapping("/cache-ready")
    public boolean cacheReady(@PathVariable("profileId") String profileId) {
        return mgr(profileId).isCacheReady();
    }

    @GetMapping("/summary")
    public HeapSummary summary(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getSummary();
    }

    @GetMapping("/histogram")
    public List<ClassHistogramEntry> histogram(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "100") int topN,
            @RequestParam(value = "sortBy", defaultValue = "SIZE") SortBy sortBy) {
        return mgr(profileId).getClassHistogram(topN, sortBy);
    }

    @PostMapping("/query")
    public OQLQueryResult query(
            @PathVariable("profileId") String profileId,
            @RequestBody OQLQueryRequest request) {
        return mgr(profileId).executeQuery(request);
    }

    @GetMapping("/threads")
    public List<HeapThreadInfo> threads(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "includeRetained", defaultValue = "false") boolean includeRetainedSize) {
        return mgr(profileId).getThreads(includeRetainedSize);
    }

    @GetMapping("/threads/{objectId}/stack")
    public List<ThreadStackFrame> getThreadStack(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId) {
        return mgr(profileId).getThreadStack(objectId);
    }

    @GetMapping("/gc-roots")
    public GCRootSummary gcRoots(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getGCRootSummary();
    }

    @PostMapping("/unload")
    public void unload(@PathVariable("profileId") String profileId) {
        mgr(profileId).unloadHeap();
    }

    @PostMapping("/delete-cache")
    public void deleteCache(@PathVariable("profileId") String profileId) {
        mgr(profileId).deleteCache();
    }

    @PostMapping("/delete")
    public void deleteHeapDump(@PathVariable("profileId") String profileId) {
        mgr(profileId).deleteHeapDump();
    }

    @PostMapping("/sanitize")
    public void sanitize(@PathVariable("profileId") String profileId) {
        mgr(profileId).sanitizeHeapDump();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadHeapDump(
            @PathVariable("profileId") String profileId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw Exceptions.invalidRequest("File is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw Exceptions.invalidRequest("Filename is required");
        }
        LOG.debug("Uploading heap dump: filename={}", filename);
        try {
            mgr(profileId).uploadHeapDump(file.getInputStream(), filename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded heap dump", e);
        }
    }

    @GetMapping("/string-analysis/exists")
    public boolean stringAnalysisExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).stringAnalysisExists();
    }

    @GetMapping("/string-analysis")
    public StringAnalysisReport getStringAnalysis(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getStringAnalysis();
    }

    @PostMapping("/string-analysis/run")
    public void runStringAnalysis(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "100") int topN) {
        mgr(profileId).runStringAnalysis(topN);
    }

    @GetMapping("/thread-analysis/exists")
    public boolean threadAnalysisExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).threadAnalysisExists();
    }

    @GetMapping("/thread-analysis")
    public ThreadAnalysisReport getThreadAnalysis(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getThreadAnalysis();
    }

    @PostMapping("/thread-analysis/run")
    public void runThreadAnalysis(@PathVariable("profileId") String profileId) {
        mgr(profileId).runThreadAnalysis();
    }

    @GetMapping("/instance/{objectId}")
    public InstanceDetail getInstanceDetail(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "includeRetained", defaultValue = "false") boolean includeRetainedSize) {
        return mgr(profileId).getInstanceDetail(objectId, includeRetainedSize);
    }

    @GetMapping("/instance/{objectId}/referrers")
    public InstanceTreeResponse getReferrers(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        return mgr(profileId).getReferrers(objectId, limit, offset);
    }

    @GetMapping("/instance/{objectId}/reachables")
    public InstanceTreeResponse getReachables(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        return mgr(profileId).getReachables(objectId, limit, offset);
    }

    @GetMapping("/instance/{objectId}/gc-root-path")
    public List<GCRootPath> getPathToGCRoot(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "excludeWeak", defaultValue = "true") boolean excludeWeakRefs,
            @RequestParam(value = "maxPaths", defaultValue = "3") int maxPaths) {
        return mgr(profileId).getPathsToGCRoot(objectId, excludeWeakRefs, maxPaths);
    }

    @PostMapping("/initialize")
    public HeapSummary initialize(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "compressedOops", required = false) Boolean compressedOops) {
        HeapDumpManager m = mgr(profileId);
        m.resolveAndStoreCompressedOops(compressedOops);
        return m.getSummary();
    }

    @GetMapping("/config")
    public HeapDumpConfig getConfig(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getHeapDumpConfig().orElse(null);
    }

    @GetMapping("/init-result/exists")
    public boolean initPipelineResultExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).initPipelineResultExists();
    }

    @GetMapping("/init-result")
    public InitPipelineResult getInitPipelineResult(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getInitPipelineResult().orElse(null);
    }

    @PostMapping("/init-result")
    public void storeInitPipelineResult(
            @PathVariable("profileId") String profileId,
            @RequestBody InitPipelineResult result) {
        mgr(profileId).storeInitPipelineResult(result);
    }

    @GetMapping("/dominator-tree")
    public DominatorTreeResponse getDominatorTreeRoots(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return mgr(profileId).getDominatorTreeRoots(limit);
    }

    @GetMapping("/dominator-tree/{objectId}/children")
    public DominatorTreeResponse getDominatorTreeChildren(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return mgr(profileId).getDominatorTreeChildren(objectId, offset, limit);
    }

    @GetMapping("/collection-analysis/exists")
    public boolean collectionAnalysisExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).collectionAnalysisExists();
    }

    @GetMapping("/collection-analysis")
    public CollectionAnalysisReport getCollectionAnalysis(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getCollectionAnalysis();
    }

    @PostMapping("/collection-analysis/run")
    public void runCollectionAnalysis(@PathVariable("profileId") String profileId) {
        mgr(profileId).runCollectionAnalysis();
    }

    @GetMapping("/class-instances")
    public ClassInstancesResponse getClassInstances(
            @PathVariable("profileId") String profileId,
            @RequestParam("className") String className,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "includeRetainedSize", defaultValue = "false") boolean includeRetainedSize) {
        return mgr(profileId).getClassInstances(className, limit, offset, includeRetainedSize);
    }

    @GetMapping("/leak-suspects/exists")
    public boolean leakSuspectsExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).leakSuspectsExists();
    }

    @GetMapping("/leak-suspects")
    public LeakSuspectsReport getLeakSuspects(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getLeakSuspects();
    }

    @PostMapping("/leak-suspects/run")
    public void runLeakSuspects(@PathVariable("profileId") String profileId) {
        mgr(profileId).runLeakSuspects();
    }

    @GetMapping("/biggest-objects/exists")
    public boolean biggestObjectsExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).biggestObjectsExists();
    }

    @GetMapping("/biggest-objects")
    public BiggestObjectsReport getBiggestObjects(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getBiggestObjects();
    }

    @PostMapping("/biggest-objects/run")
    public void runBiggestObjects(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "20") int topN) {
        mgr(profileId).runBiggestObjects(topN);
    }

    @GetMapping("/biggest-collections/exists")
    public boolean biggestCollectionsExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).biggestCollectionsExists();
    }

    @GetMapping("/biggest-collections")
    public BiggestCollectionsReport getBiggestCollections(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getBiggestCollections();
    }

    @PostMapping("/biggest-collections/run")
    public void runBiggestCollections(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "50") int topN) {
        mgr(profileId).runBiggestCollections(topN);
    }

    @GetMapping("/duplicate-objects/exists")
    public boolean duplicateObjectsExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).duplicateObjectsExists();
    }

    @GetMapping("/duplicate-objects")
    public DuplicateObjectsReport getDuplicateObjects(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getDuplicateObjects();
    }

    @PostMapping("/duplicate-objects/run")
    public void runDuplicateObjects(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "100") int topN) {
        mgr(profileId).runDuplicateObjects(topN);
    }

    @GetMapping("/classloader-analysis/exists")
    public boolean classLoaderAnalysisExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).classLoaderAnalysisExists();
    }

    @GetMapping("/classloader-analysis")
    public ClassLoaderReport getClassLoaderAnalysis(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getClassLoaderAnalysis();
    }

    @PostMapping("/classloader-analysis/run")
    public void runClassLoaderAnalysis(@PathVariable("profileId") String profileId) {
        mgr(profileId).runClassLoaderAnalysis();
    }

    @GetMapping("/consumers/exists")
    public boolean consumerReportExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).consumerReportExists();
    }

    @GetMapping("/consumers")
    public ConsumerReport getConsumerReport(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getConsumerReport();
    }

    @PostMapping("/consumers/run")
    public void runConsumerReport(@PathVariable("profileId") String profileId) {
        mgr(profileId).runConsumerReport();
    }

    private HeapDumpManager mgr(String profileId) {
        return resolver.resolve(profileId).heapDumpManager();
    }
}
