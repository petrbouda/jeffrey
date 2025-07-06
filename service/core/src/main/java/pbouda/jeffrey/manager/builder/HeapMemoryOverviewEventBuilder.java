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
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.manager.custom.model.heap.*;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.List;

public class HeapMemoryOverviewEventBuilder implements RecordBuilder<GenericRecord, HeapMemoryOverviewData> {

    private static class HeapSampleBuilder {
        private long heapUsed = -1;
        private long heapCommitted = -1;
        private long heapMax = -1;
        private long edenUsed = -1;
        private long edenCommitted = -1;
        private long edenMax = -1;
        private long survivorUsed = -1;
        private long survivorCommitted = -1;
        private long survivorMax = -1;
        private long oldGenUsed = -1;
        private long oldGenCommitted = -1;
        private long oldGenMax = -1;
        private long timestamp = -1;

        public void updateHeapSummary(ObjectNode fields, long recordTimestamp) {
            this.timestamp = recordTimestamp;
            heapUsed = fields.path("heapUsed").asLong(-1);
            heapCommitted = fields.path("heapCommitted").asLong(-1);
            heapMax = fields.path("heapMax").asLong(-1);
        }

        public void updateG1HeapSummary(ObjectNode fields, long recordTimestamp) {
            this.timestamp = recordTimestamp;
            edenUsed = fields.path("edenUsedSize").asLong(-1);
            edenCommitted = fields.path("edenTotalSize").asLong(-1);
            survivorUsed = fields.path("survivorUsedSize").asLong(-1);
            survivorCommitted = fields.path("survivorTotalSize").asLong(-1);
            oldGenUsed = fields.path("oldGenUsedSize").asLong(-1);
            oldGenCommitted = fields.path("oldGenTotalSize").asLong(-1);
        }

        public void updatePSHeapSummary(ObjectNode fields, long recordTimestamp) {
            this.timestamp = recordTimestamp;
            edenUsed = fields.path("edenSpace").path("used").asLong(-1);
            edenCommitted = fields.path("edenSpace").path("committed").asLong(-1);
            edenMax = fields.path("edenSpace").path("reserved").asLong(-1);
            survivorUsed = fields.path("fromSpace").path("used").asLong(-1) + 
                          fields.path("toSpace").path("used").asLong(-1);
            survivorCommitted = fields.path("fromSpace").path("committed").asLong(-1) + 
                               fields.path("toSpace").path("committed").asLong(-1);
            survivorMax = fields.path("fromSpace").path("reserved").asLong(-1) + 
                         fields.path("toSpace").path("reserved").asLong(-1);
            oldGenUsed = fields.path("oldSpace").path("used").asLong(-1);
            oldGenCommitted = fields.path("oldSpace").path("committed").asLong(-1);
            oldGenMax = fields.path("oldSpace").path("reserved").asLong(-1);
        }

        public boolean hasValidData() {
            return timestamp > 0 && (heapUsed >= 0 || edenUsed >= 0);
        }

        public long getTimestamp() { return timestamp; }
        public long getHeapUsed() { return heapUsed >= 0 ? heapUsed : (edenUsed + survivorUsed + oldGenUsed); }
        public long getHeapCommitted() { return heapCommitted >= 0 ? heapCommitted : (edenCommitted + survivorCommitted + oldGenCommitted); }
        public long getHeapMax() { return heapMax >= 0 ? heapMax : (edenMax + survivorMax + oldGenMax); }
        public long getEdenUsed() { return edenUsed; }
        public long getEdenCommitted() { return edenCommitted; }
        public long getEdenMax() { return edenMax; }
        public long getSurvivorUsed() { return survivorUsed; }
        public long getSurvivorCommitted() { return survivorCommitted; }
        public long getSurvivorMax() { return survivorMax; }
        public long getOldGenUsed() { return oldGenUsed; }
        public long getOldGenCommitted() { return oldGenCommitted; }
        public long getOldGenMax() { return oldGenMax; }
    }

    private final LongLongHashMap heapUsageTimeline;
    private final LongLongHashMap edenUsageTimeline;
    private final LongLongHashMap survivorUsageTimeline;
    private final LongLongHashMap oldGenUsageTimeline;
    private final RelativeTimeRange timeRange;

    private final List<HeapSampleBuilder> heapSamples = new ArrayList<>();
    private long maxHeapSize = -1;
    private long maxHeapUsed = -1;
    private long gcCount = 0;

    public HeapMemoryOverviewEventBuilder(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        this.heapUsageTimeline = TimeseriesUtils.structure(timeRange);
        this.edenUsageTimeline = TimeseriesUtils.structure(timeRange);
        this.survivorUsageTimeline = TimeseriesUtils.structure(timeRange);
        this.oldGenUsageTimeline = TimeseriesUtils.structure(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        long recordTimestamp = record.timestampFromStart().toSeconds();
        
        HeapSampleBuilder currentSample = new HeapSampleBuilder();
        
        // Process all heap summary events - we'll detect the type by available fields
        currentSample.updateHeapSummary(jsonFields, recordTimestamp);
        currentSample.updateG1HeapSummary(jsonFields, recordTimestamp);
        currentSample.updatePSHeapSummary(jsonFields, recordTimestamp);
        
        // Count GC events (only for actual GC heap summary events)
        if (jsonFields.has("heapUsed")) {
            gcCount++;
        }

        if (currentSample.hasValidData()) {
            heapSamples.add(currentSample);
            
            long heapUsed = currentSample.getHeapUsed();
            long heapCommitted = currentSample.getHeapCommitted();
            long heapMax = currentSample.getHeapMax();
            
            maxHeapUsed = Math.max(maxHeapUsed, heapUsed);
            if (heapMax > 0) {
                maxHeapSize = Math.max(maxHeapSize, heapMax);
            }

            // Update timelines
            heapUsageTimeline.put(recordTimestamp, heapUsed);
            if (currentSample.getEdenUsed() >= 0) {
                edenUsageTimeline.put(recordTimestamp, currentSample.getEdenUsed());
            }
            if (currentSample.getSurvivorUsed() >= 0) {
                survivorUsageTimeline.put(recordTimestamp, currentSample.getSurvivorUsed());
            }
            if (currentSample.getOldGenUsed() >= 0) {
                oldGenUsageTimeline.put(recordTimestamp, currentSample.getOldGenUsed());
            }
        }
    }

    @Override
    public HeapMemoryOverviewData build() {
        // Build empty allocation stats for the standard build method
        AllocationStatistics emptyAllocationStats = new AllocationStatistics(
            0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Low", BigDecimal.ZERO, List.of()
        );
        return buildWithAllocationStats(emptyAllocationStats);
    }

    public HeapMemoryOverviewData buildWithAllocationStats(AllocationStatistics allocationStats) {
        if (heapSamples.isEmpty()) {
            return buildEmptyData(allocationStats);
        }

        // Get the latest sample for current state
        HeapSampleBuilder latestSample = heapSamples.get(heapSamples.size() - 1);
        
        long currentHeapUsage = latestSample.getHeapUsed();
        long currentHeapMax = latestSample.getHeapMax();
        
        // Calculate heap usage percentage
        BigDecimal heapUsagePercentage = currentHeapMax > 0 
            ? BigDecimal.valueOf(currentHeapUsage)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(currentHeapMax), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Calculate GC frequency (collections per minute)
        Duration totalDuration = timeRange.duration();
        BigDecimal gcFrequency = totalDuration.toSeconds() > 0
            ? BigDecimal.valueOf(gcCount)
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(totalDuration.toSeconds()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Build header
        HeapMemoryHeader header = new HeapMemoryHeader(
            currentHeapUsage,
            currentHeapMax > 0 ? currentHeapMax : maxHeapSize,
            heapUsagePercentage,
            allocationStats.allocationRate(),
            allocationStats.totalBytesAllocated(),
            gcFrequency,
            gcCount,
            allocationStats.peakAllocationRate(),
            allocationStats.averageAllocationRate()
        );

        // Build current memory pool status
        List<MemoryPoolStatus> currentPools = buildCurrentPools(latestSample);

        // Build timelines
        SingleSerie mainTimeline = TimeseriesUtils.buildSerie("Heap Usage", heapUsageTimeline);
        MemoryPoolTimelines poolTimelines = new MemoryPoolTimelines(
            TimeseriesUtils.buildSerie("Eden Space", edenUsageTimeline),
            TimeseriesUtils.buildSerie("Survivor Space", survivorUsageTimeline),
            TimeseriesUtils.buildSerie("Old Generation", oldGenUsageTimeline),
            mainTimeline
        );

        return new HeapMemoryOverviewData(header, currentPools, mainTimeline, allocationStats, poolTimelines);
    }

    private List<MemoryPoolStatus> buildCurrentPools(HeapSampleBuilder latestSample) {
        List<MemoryPoolStatus> pools = new ArrayList<>();

        if (latestSample.getEdenUsed() >= 0) {
            pools.add(buildPoolStatus("Eden Space", "HEAP", 
                latestSample.getEdenUsed(), 
                latestSample.getEdenCommitted(), 
                latestSample.getEdenMax()));
        }

        if (latestSample.getSurvivorUsed() >= 0) {
            pools.add(buildPoolStatus("Survivor Space", "HEAP", 
                latestSample.getSurvivorUsed(), 
                latestSample.getSurvivorCommitted(), 
                latestSample.getSurvivorMax()));
        }

        if (latestSample.getOldGenUsed() >= 0) {
            pools.add(buildPoolStatus("Old Generation", "HEAP", 
                latestSample.getOldGenUsed(), 
                latestSample.getOldGenCommitted(), 
                latestSample.getOldGenMax()));
        }

        return pools;
    }

    private MemoryPoolStatus buildPoolStatus(String name, String type, long used, long committed, long max) {
        BigDecimal usagePercentage = max > 0 
            ? BigDecimal.valueOf(used)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(max), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new MemoryPoolStatus(name, type, used, committed, max, usagePercentage, used, used);
    }

    private HeapMemoryOverviewData buildEmptyData(AllocationStatistics allocationStats) {
        HeapMemoryHeader header = new HeapMemoryHeader(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        SingleSerie emptyTimeline = TimeseriesUtils.buildSerie("Heap Usage", new LongLongHashMap());
        MemoryPoolTimelines poolTimelines = new MemoryPoolTimelines(
            TimeseriesUtils.buildSerie("Eden Space", new LongLongHashMap()),
            TimeseriesUtils.buildSerie("Survivor Space", new LongLongHashMap()),
            TimeseriesUtils.buildSerie("Old Generation", new LongLongHashMap()),
            emptyTimeline
        );
        return new HeapMemoryOverviewData(header, Collections.emptyList(), emptyTimeline, allocationStats, poolTimelines);
    }
}
