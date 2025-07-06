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
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.manager.custom.model.heap.AllocatingClass;
import pbouda.jeffrey.manager.custom.model.heap.AllocationStatistics;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class AllocationAnalysisEventBuilder implements RecordBuilder<GenericRecord, AllocationStatistics> {

    private static class ClassAllocationData {
        private long bytesAllocated = 0;
        private long objectCount = 0;

        public void addAllocation(long bytes) {
            this.bytesAllocated += bytes;
            this.objectCount++;
        }

        public long getBytesAllocated() { return bytesAllocated; }
        public long getObjectCount() { return objectCount; }
        public BigDecimal getAverageObjectSize() {
            return objectCount > 0 
                ? BigDecimal.valueOf(bytesAllocated).divide(BigDecimal.valueOf(objectCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        }
    }

    private final ObjectLongHashMap<String> allocationsByClass = new ObjectLongHashMap<>();
    private final ObjectLongHashMap<String> objectCountsByClass = new ObjectLongHashMap<>();
    private final LongLongHashMap allocationRateTimeline = new LongLongHashMap();
    private final RelativeTimeRange timeRange;

    private long totalBytesAllocated = 0;
    private long totalObjectsAllocated = 0;
    private long peakAllocationRate = 0;
    private long currentSecondAllocations = 0;
    private long lastTimestamp = -1;

    public AllocationAnalysisEventBuilder(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        // Extract allocation data
        long tlabSize = jsonFields.path("tlabSize").asLong(0);
        String className = jsonFields.path("objectClass").path("name").asText("");
        
        if (tlabSize <= 0 || className.isEmpty()) {
            return;
        }

        // Track total allocations
        totalBytesAllocated += tlabSize;
        totalObjectsAllocated++;

        // Track allocations by class
        allocationsByClass.addToValue(className, tlabSize);
        objectCountsByClass.addToValue(className, 1);

        // Track allocation rate over time
        long currentTimestamp = record.timestampFromStart().toSeconds();
        
        if (lastTimestamp == -1 || lastTimestamp == currentTimestamp) {
            currentSecondAllocations += tlabSize;
        } else {
            // New second, record the previous second's allocation rate
            allocationRateTimeline.put(lastTimestamp, currentSecondAllocations);
            peakAllocationRate = Math.max(peakAllocationRate, currentSecondAllocations);
            currentSecondAllocations = tlabSize;
        }
        lastTimestamp = currentTimestamp;
    }

    @Override
    public AllocationStatistics build() {
        // Record the final second's allocations
        if (lastTimestamp >= 0) {
            allocationRateTimeline.put(lastTimestamp, currentSecondAllocations);
            peakAllocationRate = Math.max(peakAllocationRate, currentSecondAllocations);
        }

        // Calculate overall allocation rate (bytes per second)
        Duration totalDuration = timeRange.duration();
        BigDecimal allocationRate = totalDuration.toSeconds() > 0
            ? BigDecimal.valueOf(totalBytesAllocated)
                .divide(BigDecimal.valueOf(totalDuration.toSeconds()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Calculate average allocation rate
        BigDecimal averageAllocationRate = !allocationRateTimeline.isEmpty()
            ? BigDecimal.valueOf(allocationRateTimeline.values().sum())
                .divide(BigDecimal.valueOf(allocationRateTimeline.size()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Determine allocation pressure level
        String allocationPressure = determineAllocationPressure(allocationRate);
        BigDecimal pressureLevel = calculatePressureLevel(allocationRate);

        // Build top allocating classes (top 10)
        List<AllocatingClass> topAllocatingClasses = buildTopAllocatingClasses();

        return new AllocationStatistics(
            totalObjectsAllocated,
            totalBytesAllocated,
            allocationRate,
            BigDecimal.valueOf(peakAllocationRate),
            averageAllocationRate,
            allocationPressure,
            pressureLevel,
            topAllocatingClasses
        );
    }

    private List<AllocatingClass> buildTopAllocatingClasses() {
        List<AllocatingClass> classes = new ArrayList<>();
        
        allocationsByClass.keyValuesView().forEach(pair -> {
            String className = pair.getOne();
            long bytesAllocated = pair.getTwo();
            long objectCount = objectCountsByClass.get(className);
            
            BigDecimal percentage = totalBytesAllocated > 0
                ? BigDecimal.valueOf(bytesAllocated)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalBytesAllocated), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
                
            BigDecimal averageObjectSize = objectCount > 0
                ? BigDecimal.valueOf(bytesAllocated)
                    .divide(BigDecimal.valueOf(objectCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            classes.add(new AllocatingClass(className, bytesAllocated, objectCount, percentage, averageObjectSize));
        });

        // Sort by bytes allocated (descending) and take top 10
        return classes.stream()
            .sorted((a, b) -> Long.compare(b.bytesAllocated(), a.bytesAllocated()))
            .limit(10)
            .collect(Collectors.toList());
    }

    private String determineAllocationPressure(BigDecimal allocationRate) {
        // Define thresholds for allocation pressure (bytes per second)
        // These are rough estimates and may need tuning based on real-world data
        long ratePerSecond = allocationRate.longValue();
        
        if (ratePerSecond < 1_000_000) { // < 1MB/s
            return "Low";
        } else if (ratePerSecond < 10_000_000) { // < 10MB/s
            return "Medium";
        } else {
            return "High";
        }
    }

    private BigDecimal calculatePressureLevel(BigDecimal allocationRate) {
        // Convert allocation rate to a 0-100 scale
        // Max scale at 50MB/s allocation rate
        long maxRate = 50_000_000; // 50MB/s
        long currentRate = Math.min(allocationRate.longValue(), maxRate);
        
        return BigDecimal.valueOf(currentRate)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(maxRate), 2, RoundingMode.HALF_UP);
    }
}
