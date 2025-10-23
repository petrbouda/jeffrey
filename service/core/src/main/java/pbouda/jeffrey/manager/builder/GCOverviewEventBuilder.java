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

package pbouda.jeffrey.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.HdrHistogram.Histogram;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.GarbageCollectionCause;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.EventTypeName;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.manager.model.gc.*;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public class GCOverviewEventBuilder implements RecordBuilder<GenericRecord, GCOverviewData> {

    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /**
     * Utility method to create a BigDecimal with 2 decimal places and HALF_UP rounding
     */
    private static BigDecimal bigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    protected final LongObjectHashMap<String> cachedGCTypes = new LongObjectHashMap<>();
    protected final LongLongHashMap gcIdsWithConcurrentPhases = new LongLongHashMap();

    protected final PriorityQueue<GCEvent> longestPauses = new PriorityQueue<>(
            Comparator.comparingLong(GCEvent::getSumOfPauses));

    private final Histogram pauseTimesHistogram = new Histogram(3);
    private final LongLongHashMap gcIdToBeforeHeap = new LongLongHashMap();
    private final LongLongHashMap gcIdToAfterHeap = new LongLongHashMap();
    private final LongLongHashMap executionTimeSerie;
    private final LongLongHashMap gcCountSerie;

    private int totalCollections = 0;
    private int youngCollections = 0;
    private int oldCollections = 0;
    private long totalGcTime = 0;
    private long maxPauseTime = 0;
    private long totalMemoryFreed = 0;
    private int systemGCCalls = 0;
    private long systemGCTime = 0;
    private int diagnosticCommandCalls = 0;
    private long diagnosticCommandTime = 0;

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

        totalCollections++;
        totalGcTime += durationInNanos;
        maxPauseTime = Math.max(maxPauseTime, durationInNanos);
        pauseTimesHistogram.recordValue(durationInNanos);

        distributionCollector.record(record.duration().toMillis());

        long seconds = record.timestampFromStart().toSeconds();
        executionTimeSerie.updateValue(seconds, -1, first -> Math.max(first, durationInNanos));
        gcCountSerie.addToValue(seconds, 1);

        // Get heap usage from the maps
        long beforeGC = gcIdToBeforeHeap.getIfAbsent(gcId, 0);
        long afterGC = gcIdToAfterHeap.getIfAbsent(gcId, 0);
        long freed = beforeGC - afterGC;

        if (freed > 0) {
            totalMemoryFreed += freed;
        }

        BigDecimal efficiency = beforeGC > 0 ?
                bigDecimal((double) freed / beforeGC * 100) :
                ZERO_SCALED;

        String collectorName = Json.readString(fields, "name");
        String cause = Json.readString(fields, "cause");
        
        // Track Manual GC events (System GC and Diagnostic Command)
        if (GarbageCollectionCause.SYSTEM_GC.sameAs(cause)) {
            systemGCCalls++;
            systemGCTime += durationInNanos;
        } else if (GarbageCollectionCause.DIAGNOSTIC_COMMAND.sameAs(cause)) {
            diagnosticCommandCalls++;
            diagnosticCommandTime += durationInNanos;
        }
        
        GCEvent event = new GCEvent(
                record.startTimestamp().toEpochMilli(),
                gcId,
                getGenerationType(collectorName),
                collectorName,
                cause,
                durationInNanos,
                beforeGC,
                afterGC,
                freed,
                efficiency,
                beforeGC, // heapSize - using beforeGC as total heap size
                Json.readLong(fields, "sumOfPauses"),
                Json.readLong(fields, "longestPause"),
                gcIdsWithConcurrentPhases.containsKey(gcId)
        );

        // Add to longest pauses priority queue (min-heap, limited by maxLongestPauses)
        long sumOfPausesInNanos = Json.readLong(fields, "sumOfPauses");
        if (longestPauses.size() < maxLongestPauses) {
            longestPauses.offer(event);
        } else {
            GCEvent shortestPause = longestPauses.peek();
            if (shortestPause != null && sumOfPausesInNanos > shortestPause.getSumOfPauses()) {
                longestPauses.poll(); // Remove the shortest pause
                longestPauses.offer(event); // Add the new longer pause
            }
        }
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
        String when = fields.get("when").asText();
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
        long avgMemoryFreed = totalCollections > 0 ?
                totalMemoryFreed / totalCollections : 0;

        // Calculate GC overhead as percentage (GC time / total time * 100)
        double overheadRatio = timeRange.duration().isPositive() ? 
                (double) totalGcTime / timeRange.duration().toNanos() : 0.0;
        BigDecimal gcOverhead = bigDecimal(overheadRatio * 100);
        BigDecimal gcThroughput = bigDecimal(100.0 - (overheadRatio * 100));
        BigDecimal collectionFrequencyInSec = timeRange.duration().isPositive() ?
                bigDecimal((double) totalCollections / timeRange.duration().toSeconds()) : ZERO_SCALED;

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
                maxPauseTime,
                pauseTimesHistogram.getValueAtPercentile(0.99),
                pauseTimesHistogram.getValueAtPercentile(0.95),
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

        for (GCEvent longestPause : longestPauses) {
            long gcId = longestPause.getGcId();
            String gcType = cachedGCTypes.get(gcId);
            longestPause.setType(gcType);
        }

        // Convert PriorityQueue to sorted list (longest pauses first)
        List<GCEvent> longestPausesList = longestPauses.stream()
                .sorted(Comparator.comparingLong(GCEvent::getSumOfPauses).reversed())
                .collect(Collectors.toList());

        return new GCOverviewData(
                header,
                longestPausesList,
                distributionCollector.build(),
                efficiency,
                createGenerationStats(),
                null); // No concurrent events for base builder
    }

    private List<GCGenerationStats> createGenerationStats() {
        List<GCGenerationStats> genStats = new ArrayList<>();

        if (youngCollections > 0) {
            genStats.add(new GCGenerationStats(
                    "Young Generation",
                    youngCollections,
                    totalGcTime / 2, // Rough estimate
                    bigDecimal((double) totalGcTime / youngCollections / 1_000_000.0),
                    bigDecimal(maxPauseTime / 1_000_000.0),
                    totalMemoryFreed / 2
            ));
        }

        if (oldCollections > 0) {
            genStats.add(new GCGenerationStats(
                    "Old Generation",
                    oldCollections,
                    totalGcTime / 3, // Rough estimate
                    bigDecimal((double) totalGcTime / oldCollections / 1_000_000.0),
                    bigDecimal(maxPauseTime / 1_000_000.0),
                    totalMemoryFreed / 3
            ));
        }

        return genStats;
    }
}
