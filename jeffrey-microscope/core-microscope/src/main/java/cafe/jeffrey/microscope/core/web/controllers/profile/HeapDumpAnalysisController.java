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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderDetail;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.model.DuplicateDataReport;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

/**
 * Heap-dump analysis-report endpoints: string, thread, collection, class-loader,
 * leak-suspects, biggest-objects/collections and consumer reports — each with the
 * exists/get/run triple.
 * <p>
 * Lifecycle endpoints live in {@link HeapDumpController}; object-graph browsing
 * lives in {@link HeapDumpObjectsController}.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap")
public class HeapDumpAnalysisController {

    private final ProfileManagerResolver resolver;

    public HeapDumpAnalysisController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
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

    @GetMapping("/classloader-detail/{loaderId}")
    public ClassLoaderDetail getClassLoaderDetail(
            @PathVariable("profileId") String profileId,
            @PathVariable("loaderId") long loaderId) {
        return mgr(profileId).getClassLoaderDetail(loaderId).orElse(null);
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

    @GetMapping("/duplicate-data/exists")
    public boolean duplicateDataExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).duplicateDataExists();
    }

    @GetMapping("/duplicate-data")
    public DuplicateDataReport getDuplicateData(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getDuplicateData();
    }

    @PostMapping("/duplicate-data/run")
    public void runDuplicateData(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "50") int topN) {
        mgr(profileId).runDuplicateData(topN);
    }

    private HeapDumpManager mgr(String profileId) {
        return resolver.resolve(profileId).heapDumpManager();
    }
}
