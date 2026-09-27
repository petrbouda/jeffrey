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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.tools.RecordingSpan;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.common.event.GCConfiguration;
import cafe.jeffrey.profile.common.event.GCHeapConfiguration;
import cafe.jeffrey.profile.common.event.GCSurvivorConfiguration;
import cafe.jeffrey.profile.common.event.GCTLABConfiguration;
import cafe.jeffrey.profile.common.event.GCThreadConfiguration;
import cafe.jeffrey.profile.common.event.GCYoungGenerationConfiguration;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.model.gc.G1PlabStatistics;
import cafe.jeffrey.profile.manager.model.gc.GCPhaseParallelAggregate;
import cafe.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The garbage-collection detail pages as the agent reads them: the tables and summaries of each UI
 * page, typed, with every offset from the recording's start placed on the UTC epoch-millisecond base.
 * <p>
 * The charts' series stay on the page the answer links to. They are one point per second of the
 * recording, a shape for drawing rather than for reading, and the tables beside them carry the figures
 * a reader quotes. Several tables carry a row per collection or per event, so each keeps
 * {@value #ROWS_LIMIT} rows chosen for what the page is read for — the most recent collections for the
 * tenuring distribution, otherwise the worst by the table's own figure, worst first — and the page's
 * {@code omittedRows} says how many each table left out rather than letting the cut pass as the whole.
 */
public final class GcDetailPages {

    /** Rows carried per per-collection table; a recording with ten thousand collections would bury the rest. */
    public static final int ROWS_LIMIT = 25;

    private static final String TENURING_COLLECTIONS = "collections";
    private static final String CPU_TIMES = "cpuTimes";
    private static final String MMU = "mmu";
    private static final String EVACUATIONS = "evacuations";
    private static final String EVACUATION_FAILURES = "evacuationFailures";
    private static final String SYSTEM_GCS = "systemGcs";
    private static final String GC_LOCKERS = "gcLockers";
    private static final String STALL_SITES = "stallSites";
    private static final String CYCLES = "cycles";
    private static final String UNCOMMITS = "uncommits";
    private static final String RELOCATIONS = "relocations";
    private static final String CLASSES = "classes";
    private static final String PER_GC = "perGc";
    private static final String ROWS = "rows";

    private static final String KEPT_WORST = "worst first";
    private static final String OMITTED = "How many rows each cut table left out, keyed by table: ";
    private static final String OMITTED_EMPTY = "; empty when nothing was cut";

    private GcDetailPages() {
    }

    /**
     * What a page is rendered from: the collector's manager, and the recording span its offsets are
     * placed on, absent when the profile carries none.
     */
    record Source(GarbageCollectionManager manager, Optional<RecordingSpan> span) {

        /** The instant an offset from the recording's start falls on; null without a span to place it. */
        Long epochAt(long offsetMillis) {
            return span.map(recording -> recording.epochAt(offsetMillis)).orElse(null);
        }
    }

    static Configuration configuration(Source source) {
        GCConfigurationData data = source.manager().configuration();
        if (data == null) {
            return new Configuration(null, null, null, null, null, null, null);
        }
        return new Configuration(
                data.detectedType(),
                collector(data.collector()),
                heap(data.heap()),
                threads(data.threads()),
                survivor(data.survivor()),
                tlab(data.tlab()),
                youngGeneration(data.youngGeneration()));
    }

    static Tenuring tenuring(Source source) {
        Rows rows = new Rows();
        TenuringData data = source.manager().tenuring();
        return new Tenuring(rows.latest(TENURING_COLLECTIONS, data == null ? null : data.gcs()), rows.omitted());
    }

    static Ihop ihop(Source source) {
        Rows rows = new Rows();
        IhopData data = source.manager().ihop();
        if (data == null) {
            return new Ihop(List.of(), List.of(), rows.omitted());
        }
        return new Ihop(
                rows.worst(CPU_TIMES, data.cpuTimes(), IhopData.GcCpuEntry::realNanos, Function.identity()),
                rows.worst(MMU, data.mmu(), IhopData.MmuEntry::gcTimeNanos, entry -> mmu(source, entry)),
                rows.omitted());
    }

    static G1 g1(Source source) {
        Rows rows = new Rows();
        G1AnalysisData data = source.manager().g1Analysis();
        if (data == null) {
            return new G1(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), rows.omitted());
        }
        return new G1(
                data.header(),
                data.pausePhases() == null ? List.of() : data.pausePhases(),
                rows.worst(EVACUATIONS, data.evacuations(), G1AnalysisData.EvacuationEntry::bytesCopied,
                        Function.identity()),
                rows.worst(EVACUATION_FAILURES, data.evacuationFailures(), G1AnalysisData.EvacuationFailure::count,
                        Function.identity()),
                rows.worst(MMU, data.mmu(), IhopData.MmuEntry::gcTimeNanos, entry -> mmu(source, entry)),
                rows.worst(SYSTEM_GCS, data.systemGcs(), G1AnalysisData.SystemGcEntry::durationNanos,
                        entry -> new SystemGc(source.epochAt(entry.timeOffsetMillis()), entry.durationNanos(),
                                entry.invokedConcurrent())),
                rows.worst(GC_LOCKERS, data.gcLockers(), G1AnalysisData.GcLockerEntry::durationNanos,
                        entry -> new GcLocker(
                        source.epochAt(entry.timeOffsetMillis()), entry.durationNanos(), entry.lockCount(),
                        entry.stallCount())),
                rows.omitted());
    }

    static Zgc zgc(Source source) {
        Rows rows = new Rows();
        ZgcAnalysisData data = source.manager().zgcAnalysis();
        if (data == null) {
            return new Zgc(null, List.of(), List.of(), List.of(), List.of(), List.of(), rows.omitted());
        }
        return new Zgc(
                data.header(),
                data.stallTypes() == null ? List.of() : data.stallTypes(),
                rows.worst(STALL_SITES, data.stallSites(), ZgcAnalysisData.StallSite::totalNanos, Function.identity()),
                rows.worst(CYCLES, data.cycles(), ZgcAnalysisData.ZCycle::durationNanos, Function.identity()),
                rows.worst(UNCOMMITS, data.uncommits(), ZgcAnalysisData.ZUncommitEntry::uncommittedBytes,
                        entry -> new Uncommit(source.epochAt(entry.timeOffsetMillis()), entry.uncommittedBytes(),
                                entry.durationNanos())),
                rows.worst(RELOCATIONS, data.relocations(), ZgcAnalysisData.ZRelocationEntry::total,
                        entry -> new Relocation(
                        source.epochAt(entry.timeOffsetMillis()), entry.total(), entry.empty(), entry.relocate())),
                rows.omitted());
    }

    static StringTables stringTables(Source source) {
        StringSymbolTablesData data = source.manager().stringSymbolTables();
        if (data == null) {
            return new StringTables(null, null);
        }
        StringSymbolTablesData.Deduplication deduplication = data.deduplication();
        return new StringTables(
                data.header(),
                deduplication == null ? null : new Deduplication(
                        deduplication.cycles(),
                        deduplication.totalInspected(),
                        deduplication.totalDeduplicated(),
                        deduplication.totalNewStrings(),
                        deduplication.totalBytesSaved()));
    }

    static Finalizers finalizers(Source source) {
        Rows rows = new Rows();
        FinalizersData data = source.manager().finalizers();
        if (data == null) {
            return new Finalizers(null, List.of(), rows.omitted());
        }
        return new Finalizers(
                data.header(),
                rows.worst(CLASSES, data.classes(), FinalizersData.FinalizerClassStat::peakObjects, stat -> new FinalizerClass(
                        stat.className(), stat.codeSource(), stat.peakObjects(), stat.finalizersRun())),
                rows.omitted());
    }

    static References references(Source source) {
        Rows rows = new Rows();
        ReferenceProcessingData data = source.manager().referenceProcessing();
        if (data == null) {
            return new References(null, List.of(), List.of(), rows.omitted());
        }
        ReferenceProcessingData.Header header = data.header();
        return new References(
                header == null ? null : new ReferencesHeader(
                        header.totalReferences(), header.distinctTypes(), header.gcCount(), header.dominantType()),
                data.byType() == null ? List.of() : data.byType(),
                rows.worst(PER_GC, data.perGc(), ReferenceProcessingData.GcReferenceBreakdown::total, Function.identity()),
                rows.omitted());
    }

    static Phases phases(Source source) {
        Rows rows = new Rows();
        return new Phases(rows.worst(ROWS, source.manager().phaseParallel(), GCPhaseParallelAggregate::totalNanos,
                Function.identity()), rows.omitted());
    }

    static Plab plab(Source source) {
        Rows rows = new Rows();
        return new Plab(rows.worst(ROWS, source.manager().plabStatistics(), G1PlabStatistics::totalWasted,
                Function.identity()), rows.omitted());
    }

    private static MmuEntry mmu(Source source, IhopData.MmuEntry entry) {
        return new MmuEntry(entry.gcId(), entry.gcTimeNanos(), entry.pauseTargetNanos(),
                source.epochAt(entry.timeOffsetMillis()));
    }

    private static Collector collector(GCConfiguration collector) {
        if (collector == null) {
            return null;
        }
        return new Collector(collector.youngCollector(), collector.oldCollector(),
                collector.isExplicitGCConcurrent(), collector.isExplicitGCDisabled(), collector.pauseTarget());
    }

    private static Heap heap(GCHeapConfiguration heap) {
        if (heap == null) {
            return null;
        }
        return new Heap(heap.minSize(), heap.maxSize(), heap.initialSize(), heap.usesCompressedOops(),
                heap.compressedOopsMode(), heap.objectAlignment(), heap.heapAddressBits());
    }

    private static GcThreads threads(GCThreadConfiguration threads) {
        if (threads == null) {
            return null;
        }
        return new GcThreads(threads.parallelGCThreads(), threads.concurrentGCThreads(),
                threads.usesDynamicGCThreads());
    }

    private static Survivor survivor(GCSurvivorConfiguration survivor) {
        if (survivor == null) {
            return null;
        }
        return new Survivor(survivor.maxTenuringThreshold(), survivor.initialTenuringThreshold());
    }

    private static Tlab tlab(GCTLABConfiguration tlab) {
        if (tlab == null) {
            return null;
        }
        return new Tlab(tlab.usesTLABs(), tlab.minTLABSize(), tlab.tlabRefillWasteLimit());
    }

    private static YoungGeneration youngGeneration(GCYoungGenerationConfiguration young) {
        if (young == null) {
            return null;
        }
        return new YoungGeneration(young.maxSize(), young.minSize(), young.newRatio());
    }

    /** The rows each table keeps, and how many each left out. */
    private static final class Rows {

        private final Map<String, Integer> omitted = new LinkedHashMap<>();

        /** The most recent rows, in the order they happened. */
        <T> List<T> latest(String table, List<T> rows) {
            if (rows == null) {
                return List.of();
            }
            int from = Math.max(0, rows.size() - ROWS_LIMIT);
            note(table, from);
            return List.copyOf(rows.subList(from, rows.size()));
        }

        /** The rows largest by the table's own figure, largest first. */
        <T, R> List<R> worst(String table, List<T> rows, ToLongFunction<T> figure, Function<T, R> shape) {
            if (rows == null) {
                return List.of();
            }
            note(table, rows.size() - ROWS_LIMIT);
            return rows.stream()
                    .sorted(Comparator.comparingLong(figure).reversed())
                    .limit(ROWS_LIMIT)
                    .map(shape)
                    .toList();
        }

        private void note(String table, int left) {
            if (left > 0) {
                omitted.put(table, left);
            }
        }

        Map<String, Integer> omitted() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(omitted));
        }
    }

    /**
     * What the collector was configured with, as the JVM applied it.
     *
     * @param detectedCollector the collector Jeffrey detected from the collection events
     */
    public record Configuration(
            @McpNullable
            GarbageCollectorType detectedCollector,
            @McpNullable
            Collector collector,
            @McpNullable
            Heap heap,
            @McpNullable
            GcThreads threads,
            @McpNullable
            Survivor survivor,
            @McpNullable
            Tlab tlab,
            @McpNullable
            YoungGeneration youngGeneration) {
    }

    /**
     * @param pauseTargetNanos the pause-time goal; null when the collector has none
     */
    public record Collector(
            @McpNullable
            String youngCollector,
            @McpNullable
            String oldCollector,
            boolean explicitGcConcurrent,
            boolean explicitGcDisabled,
            @McpNullable
            Long pauseTargetNanos) {
    }

    public record Heap(
            long minSizeBytes,
            long maxSizeBytes,
            long initialSizeBytes,
            boolean usesCompressedOops,
            @McpNullable
            String compressedOopsMode,
            int objectAlignmentBytes,
            int heapAddressBits) {
    }

    public record GcThreads(
            @McpNullable
            Integer parallelGcThreads,
            @McpNullable
            Integer concurrentGcThreads,
            boolean usesDynamicGcThreads) {
    }

    public record Survivor(int maxTenuringThreshold, int initialTenuringThreshold) {
    }

    public record Tlab(boolean usesTlabs, long minTlabSizeBytes, long tlabRefillWasteLimit) {
    }

    public record YoungGeneration(
            @McpNullable
            Long maxSizeBytes,
            @McpNullable
            Long minSizeBytes,
            @McpNullable
            Integer newRatio) {
    }

    /**
     * The age histogram of each young collection, the first collections only.
     *
     * @param omittedRows how many rows each table left out, by table; empty when nothing was
     */
    public record Tenuring(
            @McpDescription("The " + ROWS_LIMIT + " most recent young collections, in order: the distribution the run ended with")
            List<TenuringData.TenuringGcSummary> collections,
            @McpDescription(OMITTED + "collections" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    public record Ihop(
            @McpDescription("The " + ROWS_LIMIT + " collections with the most real time, " + KEPT_WORST)
            List<IhopData.GcCpuEntry> cpuTimes,
            @McpDescription("The " + ROWS_LIMIT + " collections with the most GC time against the pause target, " + KEPT_WORST)
            List<MmuEntry> mmu,
            @McpDescription(OMITTED + "cpuTimes, mmu" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    /**
     * @param atEpochMs when the collection happened, as UTC epoch milliseconds; null when the profile
     *                  carries no recording span to place it on
     */
    public record MmuEntry(
            long gcId,
            long gcTimeNanos,
            long pauseTargetNanos,
            @McpNullable
            Long atEpochMs) {
    }

    public record G1(
            @McpNullable
            G1AnalysisData.G1Header header,
            List<G1AnalysisData.PausePhase> pausePhases,
            @McpDescription("The " + ROWS_LIMIT + " collections that copied the most bytes, " + KEPT_WORST)
            List<G1AnalysisData.EvacuationEntry> evacuations,
            @McpDescription("The " + ROWS_LIMIT + " collections with the most evacuation failures, " + KEPT_WORST)
            List<G1AnalysisData.EvacuationFailure> evacuationFailures,
            @McpDescription("The " + ROWS_LIMIT + " collections with the most GC time against the pause target, " + KEPT_WORST)
            List<MmuEntry> mmu,
            @McpDescription("The " + ROWS_LIMIT + " longest System.gc() collections, " + KEPT_WORST)
            List<SystemGc> systemGcs,
            @McpDescription("The " + ROWS_LIMIT + " longest GC locker stalls, " + KEPT_WORST)
            List<GcLocker> gcLockers,
            @McpDescription(OMITTED + "evacuations, evacuationFailures, mmu, systemGcs, gcLockers" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    public record SystemGc(
            @McpNullable
            Long atEpochMs,
            long durationNanos,
            boolean invokedConcurrent) {
    }

    public record GcLocker(
            @McpNullable
            Long atEpochMs,
            long durationNanos,
            int lockCount,
            int stallCount) {
    }

    public record Zgc(
            @McpNullable
            ZgcAnalysisData.ZgcHeader header,
            List<ZgcAnalysisData.StallType> stallTypes,
            @McpDescription("The " + ROWS_LIMIT + " threads that stalled longest on allocation, " + KEPT_WORST)
            List<ZgcAnalysisData.StallSite> stallSites,
            @McpDescription("The " + ROWS_LIMIT + " longest cycles, " + KEPT_WORST)
            List<ZgcAnalysisData.ZCycle> cycles,
            @McpDescription("The " + ROWS_LIMIT + " uncommits that returned the most memory, " + KEPT_WORST)
            List<Uncommit> uncommits,
            @McpDescription("The " + ROWS_LIMIT + " relocation sets with the most pages, " + KEPT_WORST)
            List<Relocation> relocations,
            @McpDescription(OMITTED + "stallSites, cycles, uncommits, relocations" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    public record Uncommit(
            @McpNullable
            Long atEpochMs,
            long uncommittedBytes,
            long durationNanos) {
    }

    public record Relocation(
            @McpNullable
            Long atEpochMs,
            long total,
            long empty,
            long relocate) {
    }

    public record StringTables(
            @McpNullable
            StringSymbolTablesData.Header header,
            @McpNullable
            Deduplication deduplication) {
    }

    public record Deduplication(
            long cycles,
            long totalInspected,
            long totalDeduplicated,
            long totalNewStrings,
            long totalBytesSaved) {
    }

    public record Finalizers(
            @McpNullable
            FinalizersData.Header header,
            @McpDescription("The " + ROWS_LIMIT + " classes with the most objects pending finalization, " + KEPT_WORST)
            List<FinalizerClass> classes,
            @McpDescription(OMITTED + "classes" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    public record FinalizerClass(
            String className,
            @McpNullable
            String codeSource,
            long peakObjects,
            long finalizersRun) {
    }

    public record References(
            @McpNullable
            ReferencesHeader header,
            List<ReferenceProcessingData.ReferenceTypeStat> byType,
            @McpDescription("The " + ROWS_LIMIT + " collections that processed the most references, " + KEPT_WORST)
            List<ReferenceProcessingData.GcReferenceBreakdown> perGc,
            @McpDescription(OMITTED + "perGc" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    public record ReferencesHeader(
            long totalReferences,
            int distinctTypes,
            long gcCount,
            @McpNullable
            String dominantType) {
    }

    public record Phases(
            @McpDescription("The " + ROWS_LIMIT + " parallel phases with the most total time, " + KEPT_WORST)
            List<GCPhaseParallelAggregate> rows,
            @McpDescription(OMITTED + "rows" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }

    public record Plab(
            @McpDescription("The " + ROWS_LIMIT + " collections that wasted the most PLAB space, " + KEPT_WORST)
            List<G1PlabStatistics> rows,
            @McpDescription(OMITTED + "rows" + OMITTED_EMPTY)
            Map<String, Integer> omittedRows) {
    }
}
