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
import cafe.jeffrey.microscope.core.profile.heapdump.model.ClassLoaderDetail;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.BiggestCollectionsAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.BiggestObjectsAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.CachedAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.ClassLoaderHeapAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.CollectionHeapAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.ConsumerReportAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.DuplicateDataAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.ThreadHeapAnalysis;

import java.util.Map;
import java.util.function.IntFunction;

/**
 * Heap-dump analysis-report endpoints. Every stored report answers the same three:
 * {@code /<report>/exists}, {@code /<report>} (null until it is computed) and {@code /<report>/run}.
 * <p>
 * Lifecycle endpoints live in {@link HeapDumpController}; object-graph browsing
 * lives in {@link HeapDumpObjectsController}.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap")
public class HeapDumpAnalysisController {

    /** The report path variable admits exactly the slugs of {@link #REPORTS}, so no other route is shadowed. */
    static final String REPORT = "{report:string-analysis|thread-analysis|collection-analysis|leak-suspects"
            + "|biggest-objects|biggest-collections|classloader-analysis|consumers|duplicate-data}";

    private static final String TOP_N = "topN";

    /**
     * A stored report as the UI names it.
     *
     * @param stored      the analysis whose stored report is looked up and read
     * @param run         the analysis a run computes, for the requested {@code topN}
     * @param defaultTopN the {@code topN} a run takes when the request names none
     */
    record StoredReport(CachedAnalysis<?> stored, IntFunction<CachedAnalysis<?>> run, int defaultTopN) {

        static StoredReport of(CachedAnalysis<?> analysis) {
            return new StoredReport(analysis, topN -> analysis, 0);
        }
    }

    static final Map<String, StoredReport> REPORTS = Map.of(
            "string-analysis", new StoredReport(new StringHeapAnalysis(), StringHeapAnalysis::new, 100),
            "thread-analysis", StoredReport.of(new ThreadHeapAnalysis()),
            "collection-analysis", StoredReport.of(new CollectionHeapAnalysis()),
            "leak-suspects", StoredReport.of(new LeakSuspectsAnalysis()),
            "biggest-objects", new StoredReport(new BiggestObjectsAnalysis(), BiggestObjectsAnalysis::new, 20),
            "biggest-collections",
            new StoredReport(new BiggestCollectionsAnalysis(), BiggestCollectionsAnalysis::new, 50),
            "classloader-analysis", StoredReport.of(new ClassLoaderHeapAnalysis()),
            "consumers", StoredReport.of(new ConsumerReportAnalysis()),
            "duplicate-data", new StoredReport(new DuplicateDataAnalysis(), DuplicateDataAnalysis::new, 50));

    private final ProfileManagerResolver resolver;

    public HeapDumpAnalysisController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    private HeapDumpManager mgr(String profileId) {
        return resolver.resolve(profileId).heapDumpManager();
    }

    @GetMapping("/" + REPORT + "/exists")
    public boolean reportExists(
            @PathVariable("profileId") String profileId,
            @PathVariable("report") String report) {
        return mgr(profileId).reportExists(REPORTS.get(report).stored());
    }

    @GetMapping("/" + REPORT)
    public Object report(
            @PathVariable("profileId") String profileId,
            @PathVariable("report") String report) {
        return mgr(profileId).report(REPORTS.get(report).stored()).orElse(null);
    }

    @PostMapping("/" + REPORT + "/run")
    public void runReport(
            @PathVariable("profileId") String profileId,
            @PathVariable("report") String report,
            @RequestParam(value = TOP_N, required = false) Integer topN) {
        StoredReport stored = REPORTS.get(report);
        mgr(profileId).runReport(stored.run().apply(topN == null ? stored.defaultTopN() : topN));
    }

    @GetMapping("/classloader-detail/{loaderId}")
    public ClassLoaderDetail getClassLoaderDetail(
            @PathVariable("profileId") String profileId,
            @PathVariable("loaderId") long loaderId) {
        return mgr(profileId).getClassLoaderDetail(loaderId).orElse(null);
    }
}
