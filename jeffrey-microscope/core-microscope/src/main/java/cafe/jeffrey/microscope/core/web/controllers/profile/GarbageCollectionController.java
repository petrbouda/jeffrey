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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.model.gc.G1PlabStatistics;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCPhaseParallelAggregate;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/gc")
public class GarbageCollectionController {

    private static final Logger LOG = LoggerFactory.getLogger(GarbageCollectionController.class);

    private final ProfileManagerResolver resolver;

    public GarbageCollectionController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public GCOverviewData overviewData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC overview");
        return mgr(profileId).overviewData();
    }

    @GetMapping("/timeseries")
    public TimeseriesData timeseries(
            @PathVariable("profileId") String profileId,
            @RequestParam("timeseriesType") GCTimeseriesType timeseriesType) {
        LOG.debug("Fetching GC timeseries: type={}", timeseriesType);
        return mgr(profileId).timeseries(timeseriesType);
    }

    @GetMapping("/configuration")
    public GCConfigurationData configuration(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC configuration");
        return mgr(profileId).configuration();
    }

    @GetMapping("/tenuring")
    public TenuringData tenuring(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC tenuring data");
        return mgr(profileId).tenuring();
    }

    @GetMapping("/ihop")
    public IhopData ihop(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching G1 IHOP data");
        return mgr(profileId).ihop();
    }

    @GetMapping("/g1")
    public G1AnalysisData g1Analysis(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching G1 deep-dive analysis");
        return mgr(profileId).g1Analysis();
    }

    @GetMapping("/zgc")
    public ZgcAnalysisData zgcAnalysis(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching ZGC deep-dive analysis");
        return mgr(profileId).zgcAnalysis();
    }

    @GetMapping("/string-symbol-tables")
    public StringSymbolTablesData stringSymbolTables(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC string/symbol table statistics");
        return mgr(profileId).stringSymbolTables();
    }

    @GetMapping("/finalizers")
    public FinalizersData finalizers(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC finalizer statistics");
        return mgr(profileId).finalizers();
    }

    @GetMapping("/reference-processing")
    public ReferenceProcessingData referenceProcessing(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC reference-processing data");
        return mgr(profileId).referenceProcessing();
    }

    @GetMapping("/phase-parallel")
    public List<GCPhaseParallelAggregate> phaseParallel(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching GC parallel sub-phase breakdown");
        return mgr(profileId).phaseParallel();
    }

    @GetMapping("/plab-statistics")
    public List<G1PlabStatistics> plabStatistics(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching G1 PLAB evacuation statistics");
        return mgr(profileId).plabStatistics();
    }

    private GarbageCollectionManager mgr(String profileId) {
        return resolver.resolve(profileId).gcManager();
    }
}
