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

package pbouda.jeffrey.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import org.eclipse.collections.api.factory.primitive.ObjectLongMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectLongMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.Event;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.profile.viewer.EventFieldsToJsonMapper;
import pbouda.jeffrey.repository.profile.ProfileEventRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class DatabaseEventPushProcessor implements EventProcessor<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseEventPushProcessor.class);

    private final ProfileEventRepository profileEventRepository;
    private final MutableObjectLongMap<String> samplesWeightsCollector = ObjectLongMaps.mutable.empty();

    private final List<Type> weightCandidates;

    private EventFieldsToJsonMapper eventFieldsToJsonMapper;

    public DatabaseEventPushProcessor(ProfileEventRepository profileEventRepository) {
        this.profileEventRepository = profileEventRepository;
        this.weightCandidates = List.of(
                Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
                Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
                Type.OBJECT_ALLOCATION_SAMPLE,
                Type.JAVA_MONITOR_ENTER,
                Type.JAVA_MONITOR_WAIT,
                Type.THREAD_PARK,
                Type.MALLOC);
    }

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public void onStart(List<EventType> eventTypes) {
        // Initialize collector for collecting samples and weights per a single event type
        for (EventType eventType : eventTypes) {
            samplesWeightsCollector.put(eventType.getName(), 0);
        }

        this.eventFieldsToJsonMapper = new EventFieldsToJsonMapper(eventTypes);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ObjectLongMaps.mutable.empty();

        // All fields of the event are mapped to JSON
        ObjectNode eventFields = eventFieldsToJsonMapper.map(event);

        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace != null) {
            jdk.jfr.EventType eventType = event.getEventType();
            Duration duration = event.getDuration();

            Event newEvent = new Event(
                    UUID.randomUUID().toString(),
                    eventType.getName(),
                    event.getStartTime().toEpochMilli(),
                    duration != Duration.ZERO ? duration.toNanos() : -1,
                    calculateSamples(event, eventType),
                    calculateWeight(event, eventType),
                    null,
                    eventFields
//                    stackTrace.getFrames().stream()
//                            .map(frame -> new Event.StackFrame(frame.getMethod().getType().getName(), frame.getMethod().getName()))
//                            .toList(),
            );

            profileEventRepository.insertEvent(newEvent);
        }

        return Result.CONTINUE;
    }

    @Override
    public void onComplete() {
        profileEventRepository.flush();
    }

    @Override
    public Void get() {
        return null;
    }

    private long calculateSamples(RecordedEvent event, EventType eventType) {
        if (Type.from(eventType) != Type.WALL_CLOCK_SAMPLE) {
            return 1;
        } else {
            return event.getLong("samples");
        }
    }

    private Long calculateWeight(RecordedEvent event, EventType eventType) {
        Type type = Type.from(eventType);
        if (isWeightBasedEvent(type)) {
            return type.weight().extractor().applyAsLong(event);
        } else {
            return null;
        }
    }

    private boolean isWeightBasedEvent(Type currentType) {
        for (Type candidate : weightCandidates) {
            if (candidate.code().equals(currentType.code())) {
                return true;
            }
        }
        return false;
    }
}
