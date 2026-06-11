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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.node.ObjectNode;
import org.HdrHistogram.Histogram;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.common.event.GarbageCollectionCause;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.profile.manager.model.gc.*;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GCOverviewEventBuilder implements RecordBuilder<GenericRecord, GCOverviewData> {

    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final long NANOS_IN_MILLI = 1_000_000L;
    private static final double NANOS_IN_MILLI_DOUBLE = 1_000_000.0;

    /**
     * Utility method to create a BigDecimal with 2 decimal places and HALF_UP rounding
     */
    private static BigDecimal bigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    protected final LongObjectHashMap<String> cachedGCTypes = new LongObjectHashMap<>();
    protected final LongLongHashMap gcIdsWithConcurrentPhases = new LongLongHashMap();

    /**
     * GC events buffered during streaming and joined with the heap-summary maps at
     * {@link #build()} time — a {@code GarbageCollection} row may stream before its
     * "After GC" {@code GCHeapSummary} row, so the join must be order-independent.
     */
    private final List<PendingGCEvent> pendingGCEvents = new ArrayList<>();

    private final Histogram pauseTimesHistogram = new Histogram(3);
    private final LongLongHashMap gcIdToBeforeHeap = new LongLongHashMap();
    private final LongLongHashMap gcIdToAfterHeap = new LongLongHashMap();
    private final LongLongHashMap executionTimeSerie;
    private final LongLongHashMap gcCountSerie;

    private final Map<GCGenerationType, GenerationStatsAccumulator> generationAccumulators =
            new EnumMap<>(GCGenerationType.class);

    private int totalCollections = 0;
    private int youngCollections = 0;
    private int oldCollections = 0;
    private int fullCollections = 0;
    private long totalGcTime = 0;
    private long maxPauseTime = 0;
    private long totalMemoryFreed = 0;
    private int systemGCCalls = 0;
    private long systemGCTime = 0;
    private int diagnosticCommandCalls = 0;
    private long diagnosticCommandTime = 0;

    /**
     * Per-GC-event values parsed during streaming; heap usage (before/after) is intentionally
     * absent — it is joined by {@code gcId} at {@link #build()} time.
     */
    private record PendingGCEvent(
            long startEpochMilli,
            long gcId,
            GCGenerationType generationType,
            String collectorName,
            String cause,
            long durationNanos,
            long sumOfPauses,
            long longestPause) {
    }

    /** Mutable per-generation aggregation of real (not estimated) GC numbers. */
    private static final class GenerationStatsAccumulator {

        private int collections;
        private long totalPauseTime;
        private long maxPause;
        private long memoryFreed;

        private void recordPauses(long pauseNanos, long longestPauseNanos) {
            collections++;
            totalPauseTime += pauseNanos;
            maxPause = Math.max(maxPause, longestPauseNanos);
        }

        private void recordFreed(long freed) {
            if (freed > 0) {
                memoryFreed += freed;
            }
        }
    }

    private final SimpleTimeDistributionCollector distributionCollector = new SimpleTimeDistributionCollector(
            new int[]{1, 5, 10, 20, 50, 100, 200, 500, 1000, Integer.MAX_VALUE},
            new String[]{"0-1ms", "1-5ms", "5-10ms", "10-20ms", "20-50ms", "50-100ms", "100-200ms", "200-500ms", "500ms-1s", "1s+"});

    private final GarbageCollectorType garbageCollector;
    private final RelativeTimeRange timeRange;
    private final int maxLongestPauses;

    private final String youngGCType;
    private final String oldGCType;

    public GCOverviewEventBuilder(
            GarbageCollectorType garbageCollector,
            RelativeTimeRange timeRange,
            int maxLongestPauses,
            Type youngGCType,
            Type oldGCType) {

        this.garbageCollector = garbageCollector;
        this.timeRange = timeRange;
        this.executionTimeSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.gcCountSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.maxLongestPauses = maxLongestPauses;
        this.youngGCType = youngGCType.code();
        this.oldGCType = oldGCType.code();
    }

    @Override
    public void onRecord(GenericRecord record) {
        String eventType = record.type().code();
        ObjectNode fields = record.jsonFields();

        if (EventTypeName.GARBAGE_COLLECTION.equals(eventType)) {
            processGCEvent(record, fields);
        } else if (EventTypeName.GC_HEAP_SUMMARY.equals(eventType)) {
            processHeapSummaryEvent(fields);
        } else if (EventTypeName.GC_PHASE_CONCURRENT.equals(eventType)) {
            processConcurrentPhaseEvent(record, fields);
        } else if (youngGCType.equals(eventType)) {
            processYoungGCEvent(record, fields, eventType);
        } else if (oldGCType.equals(eventType)) {
            processOldGCEvent(record, fields, eventType);
        }
    }

    private void processGCEvent(GenericRecord record, ObjectNode fields) {
        long durationInNanos = record.duration().toNanos();

        long gcId = Json.readLong(fields, "gcId");
        long sumOfPauses = Json.readLong(fields, "sumOfPauses");
        long longestPause = Json.readLong(fields, "longestPause");

        // The event duration includes concurrent (non-STW) phases for Z/Shenandoah/G1-concurrent
        // collections, so pause statistics prefer the dedicated STW fields and fall back to the
        // duration only when the fields are absent (Json.readLong returns -1 for absent fields).
        long pauseNanos = sumOfPauses >= 0 ? sumOfPauses : durationInNanos;
        long longestPauseNanos = longestPause >= 0 ? longestPause : durationInNanos;

        totalCollections++;
        totalGcTime += pauseNanos;
        maxPauseTime = Math.max(maxPauseTime, longestPauseNanos);
        pauseTimesHistogram.recordValue(pauseNanos);

        distributionCollector.record(pauseNanos / NANOS_IN_MILLI);

        long seconds = record.timestampFromStart().toSeconds();
        executionTimeSerie.updateValue(seconds, -1, first -> Math.max(first, durationInNanos));
        gcCountSerie.addToValue(seconds, 1);

        String collectorName = Json.readString(fields, "name");
        String cause = Json.readString(fields, "cause");

        // Track Full GC events (e.g., G1Full, SerialFull, ParallelFull)
        if (collectorName != null && collectorName.contains("Full")) {
            fullCollections++;
        }

        // Track Manual GC events (System GC and Diagnostic Command)
        if (GarbageCollectionCause.SYSTEM_GC.sameAs(cause)) {
            systemGCCalls++;
            systemGCTime += pauseNanos;
        } else if (GarbageCollectionCause.DIAGNOSTIC_COMMAND.sameAs(cause)) {
            diagnosticCommandCalls++;
            diagnosticCommandTime += pauseNanos;
        }

        GCGenerationType generationType = getGenerationType(collectorName);
        generationAccumulator(generationType).recordPauses(pauseNanos, longestPauseNanos);

        pendingGCEvents.add(new PendingGCEvent(
                record.startTimestamp().toEpochMilli(),
                gcId,
                generationType,
                collectorName,
                cause,
                durationInNanos,
                sumOfPauses,
                longestPause));
    }

    private GenerationStatsAccumulator generationAccumulator(GCGenerationType generationType) {
        return generationAccumulators.computeIfAbsent(generationType, _ -> new GenerationStatsAccumulator());
    }

    protected GCGenerationType getGenerationType(String collectorName) {
        if (garbageCollector.getYoungGenCollector().equals(collectorName)) {
            return GCGenerationType.YOUNG;
        } else {
            // Some collectors may have multiple old generation names (G1Old, G1Full, etc.)
            return GCGenerationType.OLD;
        }
    }

    private void processConcurrentPhaseEvent(GenericRecord record, ObjectNode fields) {
        long gcId = Json.readLong(fields, "gcId");
        // Mark this GC ID as having concurrent phases
        gcIdsWithConcurrentPhases.put(gcId, 1);
    }

    private void processHeapSummaryEvent(ObjectNode fields) {
        int gcId = fields.get("gcId").asInt();
        String when = fields.get("when").asString();
        long heapUsed = fields.get("heapUsed").asLong();

        if ("Before GC".equals(when)) {
            gcIdToBeforeHeap.put(gcId, heapUsed);
        } else if ("After GC".equals(when)) {
            gcIdToAfterHeap.put(gcId, heapUsed);
        }
    }

    protected void processYoungGCEvent(GenericRecord record, ObjectNode fields, String eventType) {
        youngCollections++;
    }

    private void processOldGCEvent(GenericRecord record, ObjectNode fields, String eventType) {
        oldCollections++;
    }

    @Override
    public GCOverviewData build() {
        List<GCEvent> gcEvents = joinPendingGCEvents();

        long avgMemoryFreed = totalCollections > 0 ?
                totalMemoryFreed / totalCollections : 0;

        // Calculate GC overhead as percentage (GC time / total time * 100)
        double overheadRatio = timeRange.duration().isPositive() ?
                (double) totalGcTime / timeRange.duration().toNanos() : 0.0;
        BigDecimal gcOverhead = bigDecimal(overheadRatio * 100);
        BigDecimal gcThroughput = bigDecimal(100.0 - (overheadRatio * 100));
        // toSeconds() truncates sub-second durations to 0, use a floating-point division instead
        double durationInSeconds = timeRange.duration().toMillis() / 1000.0;
        BigDecimal collectionFrequencyInSec = durationInSeconds > 0 ?
                bigDecimal(totalCollections / durationInSeconds) : ZERO_SCALED;

        // Calculate Manual GC metrics
        long totalManualGCTime = systemGCTime + diagnosticCommandTime;
        
        ManualGCCalls manualGCCalls = new ManualGCCalls(
                totalManualGCTime,
                systemGCCalls,
                diagnosticCommandCalls
        );

        GCHeader header = new GCHeader(
                totalCollections,
                youngCollections,
                oldCollections,
                fullCollections,
                maxPauseTime,
                pauseTimesHistogram.getValueAtPercentile(95.0),
                pauseTimesHistogram.getValueAtPercentile(99.0),
                totalMemoryFreed,
                avgMemoryFreed,
                gcThroughput,
                gcOverhead,
                totalGcTime,
                collectionFrequencyInSec,
                manualGCCalls
        );

        // Create efficiency data
        GCEfficiency efficiency = new GCEfficiency(
                timeRange.duration().toNanos(),
                totalGcTime,
                gcThroughput,
                gcOverhead);

        // Top-N events sorted by the sum of pauses (longest pauses first)
        List<GCEvent> longestPausesList = gcEvents.stream()
                .sorted(Comparator.comparingLong(GCEvent::getSumOfPauses).reversed())
                .limit(maxLongestPauses)
                .collect(Collectors.toList());

        for (GCEvent longestPause : longestPausesList) {
            long gcId = longestPause.getGcId();
            String gcType = cachedGCTypes.get(gcId);
            longestPause.setType(gcType);
        }

        return new GCOverviewData(
                header,
                longestPausesList,
                distributionCollector.build(),
                efficiency,
                createGenerationStats(),
                null); // No concurrent events for base builder
    }

    /**
     * Joins the buffered GC events with the heap-summary maps by {@code gcId} and accumulates
     * the join-dependent statistics (freed memory, efficiency, per-generation freed memory).
     */
    private List<GCEvent> joinPendingGCEvents() {
        List<GCEvent> gcEvents = new ArrayList<>(pendingGCEvents.size());

        for (PendingGCEvent pending : pendingGCEvents) {
            long beforeGC = gcIdToBeforeHeap.getIfAbsent(pending.gcId(), 0);
            long afterGC = gcIdToAfterHeap.getIfAbsent(pending.gcId(), 0);
            long freed = beforeGC - afterGC;

            if (freed > 0) {
                totalMemoryFreed += freed;
            }
            generationAccumulator(pending.generationType()).recordFreed(freed);

            BigDecimal efficiency = beforeGC > 0 ?
                    bigDecimal((double) freed / beforeGC * 100) :
                    ZERO_SCALED;

            gcEvents.add(new GCEvent(
                    pending.startEpochMilli(),
                    pending.gcId(),
                    pending.generationType(),
                    pending.collectorName(),
                    pending.cause(),
                    pending.durationNanos(),
                    beforeGC,
                    afterGC,
                    freed,
                    efficiency,
                    beforeGC, // heapSize - using beforeGC as total heap size
                    pending.sumOfPauses(),
                    pending.longestPause(),
                    gcIdsWithConcurrentPhases.containsKey(pending.gcId())));
        }

        return gcEvents;
    }

    private List<GCGenerationStats> createGenerationStats() {
        List<GCGenerationStats> genStats = new ArrayList<>();
        addGenerationStats(genStats, GCGenerationType.YOUNG, "Young Generation");
        addGenerationStats(genStats, GCGenerationType.OLD, "Old Generation");
        return genStats;
    }

    private void addGenerationStats(List<GCGenerationStats> genStats, GCGenerationType type, String label) {
        GenerationStatsAccumulator acc = generationAccumulators.get(type);
        if (acc == null || acc.collections == 0) {
            return;
        }
        genStats.add(new GCGenerationStats(
                label,
                acc.collections,
                acc.totalPauseTime,
                bigDecimal((double) acc.totalPauseTime / acc.collections / NANOS_IN_MILLI_DOUBLE),
                bigDecimal(acc.maxPause / NANOS_IN_MILLI_DOUBLE),
                acc.memoryFreed
        ));
    }
}
