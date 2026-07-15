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

package cafe.jeffrey.profile.manager.gc;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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

public class GarbageCollectionManagerImpl implements GarbageCollectionManager {

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
    public GarbageCollectorType garbageCollectorType() {
        List<JsonNode> gcConfigurationFields = eventRepository.eventsByTypeWithFields(Type.GC_CONFIGURATION);
        if (!gcConfigurationFields.isEmpty()) {
            JsonNode gcConfiguration = gcConfigurationFields.getFirst();

            String oldCollector = gcConfiguration.get("oldCollector").asString();
            return GarbageCollectorType.fromOldGenCollector(oldCollector);
        } else {
            throw new IllegalStateException("No GC configuration event found in the profile.");
        }
    }

    @Override
    public GCOverviewData overviewData() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        GarbageCollectorType gcType = garbageCollectorType();
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
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        GarbageCollectorType gcType = garbageCollectorType();

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
