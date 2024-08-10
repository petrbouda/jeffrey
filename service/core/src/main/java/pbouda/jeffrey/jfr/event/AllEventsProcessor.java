/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.jfr.event;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllEventsProcessor implements EventProcessor<MutableMap<String, EventTypeCollector>> {

    private static final List<WeightCandidate> WEIGHT_CANDIDATES = List.of(
            new WeightCandidate(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, "allocationSize"),
            new WeightCandidate(Type.OBJECT_ALLOCATION_SAMPLE, "weight"),
            new WeightCandidate(Type.JAVA_MONITOR_ENTER, "duration"),
            new WeightCandidate(Type.JAVA_MONITOR_WAIT, "duration"),
            new WeightCandidate(Type.THREAD_PARK, "duration")
    );

    private final ProcessableEvents processableEvents;

    private final MutableMap<String, EventTypeCollector> collectors = Maps.mutable.empty();

    public AllEventsProcessor(ProcessableEvents processableEvents) {
        this.processableEvents = processableEvents;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return processableEvents;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        EventType eventType = event.getEventType();

        // Add a newly observed collector
        EventTypeCollector collector = collectors.computeIfAbsent(eventType.getName(), __ -> {
            WeightCandidate weightCandidate = isWeightBasedEvent(event);
            if (weightCandidate == null) {
                return new EventTypeCollector(eventType);
            } else {
                return new EventTypeCollector(eventType, weightCandidate.fieldName);
            }
        });

        // Increment samples by 1
        collector.incrementSamples();

        // Increment weight (Total Allocation, Total Time)
        if (collector.isWeightBased()) {
            long weightValue = event.getLong(collector.getWeightFieldName());
            collector.incrementWeight(weightValue);
        }

        return Result.CONTINUE;
    }

    private static WeightCandidate isWeightBasedEvent(RecordedEvent recordedEvent) {
        EventType current = recordedEvent.getEventType();

        for (WeightCandidate candidate : WEIGHT_CANDIDATES) {
            if (current.getName().equals(candidate.eventType.code())) {
                return candidate;
            }
        }

        return null;
    }

    @Override
    public MutableMap<String, EventTypeCollector> get() {
        return collectors;
    }

    private record WeightCandidate(Type eventType, String fieldName) {
    }
}
