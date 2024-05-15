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

    public AllEventsProvider(Path recording) {
        this.recording = recording;
    }

    public List<EventSummary> get() {
        Map<String, EventTypeCollector> collectors = new HashMap<>();

        try (RecordingFile rec = new RecordingFile(recording)) {
            while (rec.hasMoreEvents()) {
                RecordedEvent event = rec.readEvent();
                EventType eventType = event.getEventType();

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
