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

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.gc.builder.ConcurrentGCOverviewEventBuilder;
import cafe.jeffrey.profile.manager.gc.builder.G1GCOverviewEventBuilder;
import cafe.jeffrey.profile.manager.gc.builder.GCConfigurationEventBuilder;
import cafe.jeffrey.profile.manager.gc.builder.NonConcurrentGCOverviewEventBuilder;
import cafe.jeffrey.profile.manager.model.gc.GCGenerationTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.G1PlabStatistics;
import cafe.jeffrey.profile.manager.model.gc.G1PlabStatisticsBuilder;
import cafe.jeffrey.profile.manager.model.gc.GCPhaseParallelAggregate;
import cafe.jeffrey.profile.manager.model.gc.GCPhaseParallelBuilder;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisBuilder;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizerStatsBuilder;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData;
import cafe.jeffrey.profile.manager.model.gc.tables.StringDeduplicationBuilder;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesBuilder;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData;
import cafe.jeffrey.profile.manager.model.gc.tuning.G1MmuBuilder;
import cafe.jeffrey.profile.manager.model.gc.tuning.GcCpuTimesBuilder;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData.MmuEntry;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingBuilder;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringDistributionBuilder;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisBuilder;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.Optional;

public class GarbageCollectionManagerImpl implements GarbageCollectionManager {

    private static final String OLD_COLLECTOR_FIELD = "oldCollector";
    private static final int MAX_FINALIZER_CLASSES = 100;
    private static final int MAX_LONGEST_PAUSES = 20;
    private static final int MAX_TENURING_COLLECTIONS = 50;
    private static final int MAX_GC_CPU_ENTRIES = 100;
    private static final int MAX_REFERENCE_GCS = 200;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public GarbageCollectionManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public Optional<GarbageCollectorType> garbageCollectorType() {
        List<JsonNode> gcConfigurationFields = eventRepository.eventsByTypeWithFields(Type.GC_CONFIGURATION);
        if (gcConfigurationFields.isEmpty()) {
            return Optional.empty();
        }
        String oldCollector = gcConfigurationFields.getFirst().get(OLD_COLLECTOR_FIELD).asString();
        return Optional.of(GarbageCollectorType.fromOldGenCollector(oldCollector));
    }

    @Override
    public GCOverviewData overviewData() {
        return garbageCollectorType()
                .map(this::overviewData)
                .orElseGet(GCOverviewData::empty);
    }

    private GCOverviewData overviewData(GarbageCollectorType gcType) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.GARBAGE_COLLECTION,
                        Type.GC_HEAP_SUMMARY,
                        Type.YOUNG_GARBAGE_COLLECTION,
                        Type.OLD_GARBAGE_COLLECTION,
                        Type.G1_GARBAGE_COLLECTION,
                        Type.GC_PHASE_CONCURRENT
                ))
                .withJsonFields();

        RecordBuilder<GenericRecord, GCOverviewData> builder = switch (gcType) {
            case SERIAL -> nonConcurrentGCBuilder(GarbageCollectorType.SERIAL, timeRange);
            case PARALLEL -> nonConcurrentGCBuilder(GarbageCollectorType.PARALLEL, timeRange);
            case G1 -> new G1GCOverviewEventBuilder(timeRange, MAX_LONGEST_PAUSES);
            case Z -> concurrentGCBuilder(GarbageCollectorType.Z, timeRange);
            case SHENANDOAH -> concurrentGCBuilder(GarbageCollectorType.SHENANDOAH, timeRange);
            case ZGENERATIONAL -> concurrentGCBuilder(GarbageCollectorType.ZGENERATIONAL, timeRange);
        };

        return eventStreamRepository.genericStreaming(configurer, builder);
    }

    private NonConcurrentGCOverviewEventBuilder nonConcurrentGCBuilder(GarbageCollectorType gcType, RelativeTimeRange timeRange) {
        return new NonConcurrentGCOverviewEventBuilder(
                gcType,
                timeRange,
                MAX_LONGEST_PAUSES,
                Type.YOUNG_GARBAGE_COLLECTION,
                Type.OLD_GARBAGE_COLLECTION);
    }

    private ConcurrentGCOverviewEventBuilder concurrentGCBuilder(GarbageCollectorType gcType, RelativeTimeRange timeRange) {
        return new ConcurrentGCOverviewEventBuilder(
                gcType,
                timeRange,
                MAX_LONGEST_PAUSES,
                Type.YOUNG_GARBAGE_COLLECTION,
                Type.OLD_GARBAGE_COLLECTION);
    }

    @Override
    public TimeseriesData timeseries(GCTimeseriesType timeseriesType) {
        return garbageCollectorType()
                .map(gcType -> timeseries(timeseriesType, gcType))
                .orElseGet(TimeseriesData::empty);
    }

    private TimeseriesData timeseries(GCTimeseriesType timeseriesType, GarbageCollectorType gcType) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.GARBAGE_COLLECTION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer,
                new GCGenerationTimeseriesBuilder(timeRange, timeseriesType, gcType));
    }

    @Override
    public GCConfigurationData configuration() {
        // The builder keeps the last streamed configuration per type ("latest wins"), so the stream
        // must be chronological — the events table is physically clustered, not guaranteed time-ordered
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.GC_CONFIGURATION,
                        Type.GC_HEAP_CONFIGURATION,
                        Type.GC_TLAB_CONFIGURATION,
                        Type.GC_SURVIVOR_CONFIGURATION,
                        Type.YOUNG_GENERATION_CONFIGURATION
                ))
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new GCConfigurationEventBuilder());
    }

    @Override
    public TenuringData tenuring() {
        EventQueryConfigurer tenuringConfigurer = new EventQueryConfigurer()
                .withEventType(Type.TENURING_DISTRIBUTION)
                .withJsonFields();
        var gcs = eventStreamRepository.genericStreaming(
                tenuringConfigurer, new TenuringDistributionBuilder(MAX_TENURING_COLLECTIONS));

        return new TenuringData(gcs);
    }

    @Override
    public ReferenceProcessingData referenceProcessing() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.GC_REFERENCE_STATISTICS)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new ReferenceProcessingBuilder(timeRange, MAX_REFERENCE_GCS));
    }

    @Override
    public IhopData ihop() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer ihopConfigurer = new EventQueryConfigurer()
                .withEventType(Type.G1_ADAPTIVE_IHOP)
                .withJsonFields();
        TimeseriesData ihopTimeline =
                eventStreamRepository.genericStreaming(ihopConfigurer, new IhopTimeseriesBuilder(timeRange));

        EventQueryConfigurer cpuConfigurer = new EventQueryConfigurer()
                .withEventType(Type.GC_CPU_TIME)
                .withJsonFields();
        var cpuTimes = eventStreamRepository.genericStreaming(cpuConfigurer, new GcCpuTimesBuilder(MAX_GC_CPU_ENTRIES));

        EventQueryConfigurer mmuConfigurer = new EventQueryConfigurer()
                .withEventType(Type.G1_MMU)
                .withJsonFields();
        var mmu = eventStreamRepository.genericStreaming(mmuConfigurer, new G1MmuBuilder(MAX_GC_CPU_ENTRIES));

        return new IhopData(ihopTimeline, cpuTimes, mmu);
    }

    @Override
    public G1AnalysisData g1Analysis() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.GARBAGE_COLLECTION,
                        Type.G1_GARBAGE_COLLECTION,
                        Type.GC_PHASE_PAUSE,
                        Type.GC_PHASE_PAUSE_LEVEL_1,
                        Type.GC_PHASE_PAUSE_LEVEL_2,
                        Type.GC_PHASE_PAUSE_LEVEL_3,
                        Type.GC_PHASE_PAUSE_LEVEL_4,
                        Type.GC_PHASE_PARALLEL,
                        Type.G1_HEAP_SUMMARY,
                        Type.G1_HEAP_REGION_INFORMATION,
                        Type.EVACUATION_INFORMATION,
                        Type.EVACUATION_FAILED,
                        Type.SYSTEM_GC,
                        Type.GC_LOCKER))
                .withJsonFields();
        G1AnalysisData base = eventStreamRepository.genericStreaming(configurer, new G1AnalysisBuilder(timeRange));

        EventQueryConfigurer ihopConfigurer = new EventQueryConfigurer()
                .withEventType(Type.G1_ADAPTIVE_IHOP)
                .withJsonFields();
        TimeseriesData ihopTimeline =
                eventStreamRepository.genericStreaming(ihopConfigurer, new IhopTimeseriesBuilder(timeRange));

        EventQueryConfigurer mmuConfigurer = new EventQueryConfigurer()
                .withEventType(Type.G1_MMU)
                .withJsonFields();
        List<MmuEntry> mmu = eventStreamRepository.genericStreaming(mmuConfigurer, new G1MmuBuilder(MAX_GC_CPU_ENTRIES));

        return new G1AnalysisData(
                base.header(),
                base.pausePhases(),
                base.regionComposition(),
                base.regionSnapshots(),
                base.evacuations(),
                base.evacuationFailures(),
                ihopTimeline,
                mmu,
                base.systemGcs(),
                base.gcLockers());
    }

    @Override
    public ZgcAnalysisData zgcAnalysis() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.Z_ALLOCATION_STALL,
                        Type.Z_YOUNG_GARBAGE_COLLECTION,
                        Type.Z_OLD_GARBAGE_COLLECTION,
                        Type.Z_PAGE_ALLOCATION,
                        Type.Z_UNCOMMIT,
                        Type.Z_RELOCATION_SET))
                .withJsonFields()
                .withThreads();

        return eventStreamRepository.genericStreaming(configurer, new ZgcAnalysisBuilder(timeRange));
    }

    @Override
    public StringSymbolTablesData stringSymbolTables() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.STRING_TABLE_STATISTICS, Type.SYMBOL_TABLE_STATISTICS))
                .withJsonFields();

        StringSymbolTablesData tables =
                eventStreamRepository.genericStreaming(configurer, new StringSymbolTablesBuilder(timeRange));

        EventQueryConfigurer dedupConfigurer = new EventQueryConfigurer()
                .withEventType(Type.STRING_DEDUPLICATION)
                .withJsonFields();

        StringSymbolTablesData.Deduplication deduplication =
                eventStreamRepository.genericStreaming(dedupConfigurer, new StringDeduplicationBuilder(timeRange));

        return tables.withDeduplication(deduplication);
    }

    @Override
    public FinalizersData finalizers() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.FINALIZER_STATISTICS)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new FinalizerStatsBuilder(MAX_FINALIZER_CLASSES));
    }

    @Override
    public List<GCPhaseParallelAggregate> phaseParallel() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.GC_PHASE_PARALLEL)
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new GCPhaseParallelBuilder());
    }

    @Override
    public List<G1PlabStatistics> plabStatistics() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.G1_EVACUATION_YOUNG_STATISTICS,
                        Type.G1_EVACUATION_OLD_STATISTICS))
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new G1PlabStatisticsBuilder());
    }
}
