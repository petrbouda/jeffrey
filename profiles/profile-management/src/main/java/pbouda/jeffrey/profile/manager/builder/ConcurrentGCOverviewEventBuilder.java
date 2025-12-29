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

package pbouda.jeffrey.profile.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.shared.Json;
import pbouda.jeffrey.shared.model.EventTypeName;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;
import pbouda.jeffrey.profile.manager.model.gc.ConcurrentEvent;
import pbouda.jeffrey.profile.manager.model.gc.ConcurrentPhase;
import pbouda.jeffrey.profile.manager.model.gc.GCOverviewData;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;

import java.util.*;
import java.util.stream.Collectors;

public class ConcurrentGCOverviewEventBuilder extends GCOverviewEventBuilder {

    private static final int MAX_LONGEST_CONCURRENT_EVENTS = 20;

    private final LongObjectHashMap<List<ConcurrentPhase>> concurrentPhasesByGcId = new LongObjectHashMap<>();
    private final LongObjectHashMap<String> gcNames = new LongObjectHashMap<>();
    private final LongObjectHashMap<Long> gcSumOfPauses = new LongObjectHashMap<>();

    public ConcurrentGCOverviewEventBuilder(
            GarbageCollectorType garbageCollector,
            RelativeTimeRange timeRange,
            int maxLongestPauses,
            Type youngGCType,
            Type oldGCType) {
        super(garbageCollector, timeRange, maxLongestPauses, youngGCType, oldGCType);
    }

    @Override
    public void onRecord(GenericRecord record) {
        super.onRecord(record);

        String eventType = record.type().code();
        ObjectNode fields = record.jsonFields();

        if (EventTypeName.GC_PHASE_CONCURRENT.equals(eventType)) {
            processConcurrentPhaseEventForDetails(record, fields);
        } else if (EventTypeName.GARBAGE_COLLECTION.equals(eventType)) {
            // Store GC names and sumOfPauses for concurrent events
            long gcId = Json.readLong(fields, "gcId");
            String name = Json.readString(fields, "name");
            long sumOfPauses = Json.readLong(fields, "sumOfPauses");
            
            gcNames.put(gcId, name);
            gcSumOfPauses.put(gcId, sumOfPauses);
        }
    }

    private void processConcurrentPhaseEventForDetails(GenericRecord record, ObjectNode fields) {
        long gcId = Json.readLong(fields, "gcId");
        String phaseName = Json.readString(fields, "name");
        long duration = record.duration().toNanos();
        long timestamp = record.startTimestamp().toEpochMilli();
        long timestampFromStart = record.timestampFromStart().toNanos();

        ConcurrentPhase phase = new ConcurrentPhase(phaseName, duration, timestamp, timestampFromStart);

        concurrentPhasesByGcId.updateValue(gcId, ArrayList::new, phases -> {
            phases.add(phase);
            return phases;
        });
    }

    @Override
    public GCOverviewData build() {
        // Build the base overview data
        GCOverviewData baseData = super.build();

        // Build concurrent events
        List<ConcurrentEvent> concurrentEvents = buildConcurrentEvents();

        return new GCOverviewData(
                baseData.header(),
                baseData.longestPauses(),
                baseData.pauseDistribution(),
                baseData.efficiency(),
                baseData.generationStats(),
                concurrentEvents
        );
    }

    private List<ConcurrentEvent> buildConcurrentEvents() {
        List<ConcurrentEvent> events = new ArrayList<>();

        concurrentPhasesByGcId.forEachKeyValue((gcId, phases) -> {
            if (phases != null && !phases.isEmpty()) {
                // Calculate total duration for this concurrent cycle
                long totalDuration = phases.stream()
                        .mapToLong(ConcurrentPhase::duration)
                        .sum();

                // Find the earliest timestamp
                long earliestTimestamp = phases.stream()
                        .mapToLong(ConcurrentPhase::timestamp)
                        .min()
                        .orElse(0);

                long earliestTimestampFromStart = phases.stream()
                        .mapToLong(ConcurrentPhase::timestampFromStart)
                        .min()
                        .orElse(0);

                String name = gcNames.getIfAbsent(gcId, () -> "Concurrent GC");
                long sumOfPauses = gcSumOfPauses.getIfAbsent(gcId, () -> 0L);

                ConcurrentEvent event = new ConcurrentEvent(
                        gcId,
                        name,
                        getGenerationType(name),
                        totalDuration,
                        earliestTimestamp,
                        earliestTimestampFromStart,
                        sumOfPauses,
                        phases
                );

                events.add(event);
            }
        });

        // Sort by duration (longest first) and limit to MAX_LONGEST_CONCURRENT_EVENTS
        return events.stream()
                .sorted(Comparator.comparingLong(ConcurrentEvent::duration).reversed())
                .limit(MAX_LONGEST_CONCURRENT_EVENTS)
                .collect(Collectors.toList());
    }
}
