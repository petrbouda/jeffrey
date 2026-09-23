/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.gc;

import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.manager.model.gc.G1PlabStatistics;
import cafe.jeffrey.profile.manager.model.gc.GCPhaseParallelAggregate;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

public interface GarbageCollectionManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, GarbageCollectionManager> {
    }

    GarbageCollectorType garbageCollectorType();

    GCOverviewData overviewData();

    TimeseriesData timeseries(GCTimeseriesType timeseriesType);

    GCConfigurationData configuration();

    /**
     * Promotion/tenuring deep-tuning data: per-collection survivor-age distributions
     * ({@code jdk.TenuringDistribution}).
     */
    TenuringData tenuring();

    /**
     * G1 IHOP deep-tuning data: marking-start threshold vs old-gen occupancy timeline
     * ({@code jdk.G1AdaptiveIHOP}) and per-collection CPU times ({@code jdk.GCCPUTime}).
     * Collectors without IHOP produce an empty timeline.
     */
    IhopData ihop();

    /**
     * G1-specific deep-dive: pause-phase anatomy ({@code jdk.GCPhasePause*}), heap-region
     * composition and per-region snapshots ({@code jdk.G1HeapSummary}, {@code jdk.G1HeapRegionInformation}),
     * evacuation cost and to-space exhaustion ({@code jdk.EvacuationInformation}/{@code EvacuationFailed}),
     * IHOP/MMU marking behaviour, plus explicit-GC and GC-locker anomalies. Non-G1 recordings
     * produce an empty result.
     */
    G1AnalysisData g1Analysis();

    /**
     * ZGC-specific deep-dive: allocation stalls ({@code jdk.ZAllocationStall}), young/old cycles,
     * page-allocation throughput, uncommit and relocation activity. Non-ZGC recordings produce an
     * empty result.
     */
    ZgcAnalysisData zgcAnalysis();

    /**
     * String/symbol intern-table footprint over time, from {@code jdk.StringTableStatistics} and
     * {@code jdk.SymbolTableStatistics}. Surfaces interned-string leaks and symbol-table growth.
     */
    StringSymbolTablesData stringSymbolTables();

    /**
     * Per-class finalization statistics from {@code jdk.FinalizerStatistics}: peak pending
     * finalizable objects and total finalizers run, ranked by pending objects.
     */
    FinalizersData finalizers();

    /**
     * Reference-processing data from {@code jdk.GCReferenceStatistics}: Soft/Weak/Final/Phantom
     * reference counts as per-type totals, a per-second timeline, and a per-collection breakdown.
     */
    ReferenceProcessingData referenceProcessing();

    /**
     * Parallel GC sub-phase breakdown ({@code jdk.GCPhaseParallel}) aggregated by phase name across all
     * worker threads and collections, longest total first. Empty when the event is not recorded
     * (parallel-phase detail is off in many configs).
     */
    List<GCPhaseParallelAggregate> phaseParallel();

    /**
     * G1 PLAB (promotion-buffer) evacuation statistics, per young/old evacuation
     * ({@code jdk.G1EvacuationYoungStatistics} / {@code jdk.G1EvacuationOldStatistics}) — allocated vs.
     * wasted buffer bytes and waste %. Empty for non-G1 recordings or when the (Detailed-category)
     * events are not enabled.
     */
    List<G1PlabStatistics> plabStatistics();
}
