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

package pbouda.jeffrey.profile.summary.event;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.util.List;
import java.util.function.Function;

public class AllEventsProcessor implements EventProcessor<MutableMap<String, EventTypeCollector>> {

    private final List<WeightCandidate> weightCandidates;

    private final ProcessableEvents processableEvents;

    private final MutableMap<String, EventTypeCollector> collectors = Maps.mutable.empty();

    public AllEventsProcessor(ProcessableEvents processableEvents) {
        this.processableEvents = processableEvents;

        this.weightCandidates = List.of(
                new WeightCandidate(Type.OBJECT_ALLOCATION_IN_NEW_TLAB),
                new WeightCandidate(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB),
                new WeightCandidate(Type.OBJECT_ALLOCATION_SAMPLE),
                new WeightCandidate(Type.JAVA_MONITOR_ENTER),
                new WeightCandidate(Type.JAVA_MONITOR_WAIT),
                new WeightCandidate(Type.THREAD_PARK)
        );
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
                return new EventTypeCollector(eventType, weightCandidate.weightExtractor);
            }
        });

        // Increment samples by 1, or the number of samples in the event (if the field is present)
        if (event.hasField("samples")) {
            int samples = event.getInt("samples");
            collector.incrementSamples(samples);
        } else {
            collector.incrementSamples(1);
        }

        // Increment weight (Total Allocation, Total Time, etc.)
        collector.incrementWeight(event);

        return Result.CONTINUE;
    }

    private WeightCandidate isWeightBasedEvent(RecordedEvent recordedEvent) {
        EventType current = recordedEvent.getEventType();
        for (WeightCandidate candidate : weightCandidates) {
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

    private record WeightCandidate(Type eventType, Function<RecordedEvent, Long> weightExtractor) {
        private WeightCandidate(Type eventType) {
            this(eventType, eventType.weightExtractor());
        }
    }
}
