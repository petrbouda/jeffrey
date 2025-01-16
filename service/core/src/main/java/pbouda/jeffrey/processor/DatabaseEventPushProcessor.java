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
import jdk.jfr.consumer.*;
import org.eclipse.collections.api.factory.primitive.ObjectLongMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectLongMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.*;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.profile.viewer.EventFieldsToJsonMapper;
import pbouda.jeffrey.repository.profile.BatchingDatabaseWriter;

import java.time.Duration;
import java.util.*;

public class DatabaseEventPushProcessor implements EventProcessor<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseEventPushProcessor.class);

    private final BatchingDatabaseWriter<Event> eventRepository;
    private final BatchingDatabaseWriter<EventStacktrace> stacktraceRepository;
    private final BatchingDatabaseWriter<EventThread> threadRepository;

    private final MutableObjectLongMap<String> samplesWeightsCollector = ObjectLongMaps.mutable.empty();

    private final Map<RecordedStackTrace, EventStacktrace> stacktraces = new IdentityHashMap<>();
    private final Map<RecordedThread, EventThread> threads = new IdentityHashMap<>();

    private final List<Type> weightCandidates;

    private EventFieldsToJsonMapper eventFieldsToJsonMapper;

    public DatabaseEventPushProcessor(
            BatchingDatabaseWriter<Event> eventRepository,
            BatchingDatabaseWriter<EventStacktrace> stacktraceRepository,
            BatchingDatabaseWriter<EventThread> threadRepository) {

        this.eventRepository = eventRepository;
        this.stacktraceRepository = stacktraceRepository;
        this.threadRepository = threadRepository;

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

        jdk.jfr.EventType eventType = event.getEventType();
        Duration duration = event.getDuration();

        RecordedStackTrace stackTrace = event.getStackTrace();
        EventStacktrace eventStacktrace = null;
        if (stackTrace != null) {
            eventStacktrace = stacktraces.computeIfAbsent(
                    stackTrace, DatabaseEventPushProcessor::mapStacktrace);
        }

        RecordedThread thread = event.getThread();
        EventThread eventThread = null;
        if (thread != null) {
            eventThread = threads.computeIfAbsent(thread, DatabaseEventPushProcessor::mapThread);
        }

        Event newEvent = new Event(
                null,
                eventType.getName(),
                event.getStartTime().toEpochMilli(),
                duration != Duration.ZERO ? duration.toNanos() : -1,
                calculateSamples(event, eventType),
                calculateWeight(event, eventType),
                eventStacktrace != null ? eventStacktrace.stacktraceId() : null,
                eventThread != null ? eventThread.threadId() : null,
                eventFields
        );
        eventRepository.insert(newEvent);

        return Result.CONTINUE;
    }

    private static EventStacktrace mapStacktrace(RecordedStackTrace stackTrace) {
        List<EventFrame> frames = new ArrayList<>();
        for (RecordedFrame recordedFrame : stackTrace.getFrames().reversed()) {
            RecordedMethod method = recordedFrame.getMethod();
            EventFrame eventFrame = new EventFrame(
                    method.getType().getName(),
                    method.getName(),
                    FrameType.fromCode(recordedFrame.getType()),
                    recordedFrame.getBytecodeIndex(),
                    recordedFrame.getLineNumber());

            frames.add(eventFrame);
        }

        return new EventStacktrace(UUID.randomUUID().toString(), null, null, frames);
    }

    private static EventThread mapThread(RecordedThread thread) {
        long osThreadId = thread.getOSThreadId();
        long javaThreadId = thread.getJavaThreadId();
        return new EventThread(
                UUID.randomUUID().toString(),
                osThreadId < 0 ? null : osThreadId,
                thread.getOSName(),
                javaThreadId < 0 ? null : javaThreadId,
                thread.getJavaName(),
                thread.isVirtual());
    }

    @Override
    public void onComplete() {
        try {
            stacktraceRepository.start();
            threadRepository.start();

            stacktraces.values().forEach(stacktraceRepository::insert);
            threads.values().forEach(threadRepository::insert);

            eventRepository.close();
            stacktraceRepository.close();
            threadRepository.close();
        } catch (Exception e) {
            throw new RuntimeException("Cannot finish writing events to Database", e);
        }
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
