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
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.common.Type;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class AllEventsProvider implements Supplier<List<EventSummary>> {

    private static final List<WeightCandidate> WEIGHT_CANDIDATES = List.of(
            new WeightCandidate(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, "allocationSize"),
            new WeightCandidate(Type.OBJECT_ALLOCATION_SAMPLE, "weight"),
            new WeightCandidate(Type.JAVA_MONITOR_ENTER, "duration"),
            new WeightCandidate(Type.JAVA_MONITOR_WAIT, "duration"),
            new WeightCandidate(Type.THREAD_PARK, "duration")
    );

    private final Path recording;
    private final List<Type> supportedEvents;

    public AllEventsProvider(Path recording) {
        this(recording, null);
    }

    public AllEventsProvider(Path recording, List<Type> supportedEvents) {
        this.recording = recording;
        this.supportedEvents = supportedEvents;
    }

    public List<EventSummary> get() {
        Map<String, EventTypeCollector> collectors = new HashMap<>();

        try (RecordingFile rec = new RecordingFile(recording)) {
            while (rec.hasMoreEvents()) {
                RecordedEvent event = rec.readEvent();
                EventType eventType = event.getEventType();

                if (!isSupportedEvent(eventType)) {
                    continue;
                }

                // Add a newly observed collector
                EventTypeCollector collector = collectors.computeIfAbsent(eventType.getName(), _ -> {
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
                    long weightValue = event.getLong(collector.weightFieldName);
                    collector.incrementWeight(weightValue);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return collectors.values().stream()
                .map(EventTypeCollector::buildSummary)
                .toList();
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

    private boolean isSupportedEvent(EventType eventType) {
        if (supportedEvents == null) {
            return true;
        }

        for (Type event : supportedEvents) {
            if (event.sameAs(eventType)) {
                return true;
            }
        }

        return false;
    }

    private static class EventTypeCollector {

        private final EventType eventType;
        private final String weightFieldName;

        private long samples = 0L;
        private long weight = 0L;

        public EventTypeCollector(EventType eventType) {
            this(eventType, null);
        }

        public EventTypeCollector(EventType eventType, String weightFieldName) {
            this.eventType = eventType;
            this.weightFieldName = weightFieldName;
        }

        public void incrementSamples() {
            samples++;
        }

        public void incrementWeight(long weight) {
            this.weight += weight;
        }

        public boolean isWeightBased() {
            return weightFieldName != null;
        }

        public EventSummary buildSummary() {
            return new EventSummary(eventType, samples, isWeightBased() ? weight : -1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EventTypeCollector that)) {
                return false;
            }
            return Objects.equals(eventType.getLabel(), that.eventType.getLabel());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(eventType.getLabel());
        }
    }

    private record WeightCandidate(Type eventType, String fieldName) {
    }
}
