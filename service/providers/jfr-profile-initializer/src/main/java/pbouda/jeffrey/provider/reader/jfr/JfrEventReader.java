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

package pbouda.jeffrey.provider.reader.jfr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.*;
import pbouda.jeffrey.common.model.ProfilingStartEnd;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.api.model.*;
import pbouda.jeffrey.provider.reader.jfr.fields.EventFieldsToJsonMapper;
import pbouda.jeffrey.provider.reader.jfr.fields.EventTypeUtils;
import pbouda.jeffrey.provider.reader.jfr.stacktrace.StacktraceTypeResolver;
import pbouda.jeffrey.provider.reader.jfr.stacktrace.StacktraceTypeResolverImpl;
import pbouda.jeffrey.provider.reader.jfr.tag.IdleStacktraceTagResolver;
import pbouda.jeffrey.provider.reader.jfr.tag.StacktraceTagResolver;
import pbouda.jeffrey.provider.reader.jfr.tag.UnsafeAllocationStacktraceTagResolver;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class JfrEventReader implements EventProcessor<Void> {

    private static final List<StacktraceTagResolver> tagResolvers = List.of(
            new UnsafeAllocationStacktraceTagResolver(),
            new IdleStacktraceTagResolver()
    );

    private final long recordingStartedAt;
    private final SingleThreadedEventWriter writer;

    private final Map<Long, EventThread> threads = new HashMap<>();
    private final Map<RecordedStackTrace, Long> stacktracesById = new IdentityHashMap<>();
    private final Map<RecordedThread, Long> threadsById = new IdentityHashMap<>();

    private final Supplier<StacktraceTypeResolver> stacktraceTypeResolverSupplier = StacktraceTypeResolverImpl::new;

    private ActiveSettingsResolver activeSettingsResolver;
    private EventFieldsToJsonMapper eventFieldsToJsonMapper;

    public JfrEventReader(
            ProfilingStartEnd profilingStartEnd,
            SingleThreadedEventWriter writer) {

        this.recordingStartedAt = profilingStartEnd.start().toEpochMilli();
        this.writer = writer;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public void onStart(List<jdk.jfr.EventType> eventTypes) {
        this.activeSettingsResolver = new ActiveSettingsResolver(eventTypes);
        this.eventFieldsToJsonMapper = new EventFieldsToJsonMapper(eventTypes);
        this.writer.onThreadStart();

        for (jdk.jfr.EventType eventType : eventTypes) {
            JsonNode columns = EventTypeUtils.toColumns(eventType);
            EventType newEventType = new EventType(
                    eventType.getName(),
                    eventType.getLabel(),
                    eventType.getId(),
                    eventType.getDescription(),
                    eventType.getCategoryNames(),
                    eventType.getField("stackTrace") != null,
                    columns);

            this.writer.onEventType(newEventType);
        }
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Type type = Type.fromCode(event.getEventType().getName());

        if (type == Type.ACTIVE_SETTING) {
            EventSetting resolveSetting = activeSettingsResolver.resolve(event);
            if (resolveSetting != null) {
                writer.onEventSetting(resolveSetting);
            }
        }

        // All fields of the event are mapped to JSON
        ObjectNode eventFields = eventFieldsToJsonMapper.map(event);

        long samples = calculateSamples(event, type);
        Long weight = calculateWeight(event, type);

        // Entity (very likely a class) that is associated with the weight (causes the event, e.g. allocated class)
        String weightEntity = retrieveWeightEntity(event, type);

        /*
         * The thread is resolved based on the thread of the event.
         */
        RecordedThread recordedThread = resolveThread(event);
        Long threadId = null;
        if (recordedThread != null) {
            threadId = threadsById.computeIfAbsent(recordedThread, rt -> {
                EventThread thread = mapThread(recordedThread);
                long newThreadId = writer.onEventThread(thread);
                threads.put(newThreadId, thread);
                return newThreadId;
            });
        }

        /*
         * Resolve stacktrace and cache it to deduplicate the same stacktraces.
         */
        RecordedStackTrace stackTrace = event.getStackTrace();
        Long stacktraceId = null;
        if (stackTrace != null && threadId != null) {
            EventThread eventThread = threads.get(threadId);
            stacktraceId = stacktracesById.computeIfAbsent(stackTrace, st -> {
                EventStacktrace eventStacktrace = mapStacktrace(type, eventThread, st);
                eventStacktrace.addStacktraceTags(resolveStacktraceTags(stackTrace));
                return writer.onEventStacktrace(eventStacktrace);
            });
        }

        Instant startTime = event.getStartTime();
        long eventTimestamp = startTime.toEpochMilli();
        long timestampFromStart = eventTimestamp - recordingStartedAt;

        Duration duration = event.getDuration();
        Event newEvent = new Event(
                type.code(),
                eventTimestamp,
                timestampFromStart,
                duration != Duration.ZERO ? duration.toNanos() : null,
                samples,
                weight,
                weightEntity,
                stacktraceId,
                threadId,
                eventFields
        );
        writer.onEvent(newEvent);
        return Result.CONTINUE;
    }

    private EventStacktrace mapStacktrace(Type type, EventThread eventThread, RecordedStackTrace stacktrace) {
        StacktraceTypeResolver stacktraceTypeResolver = stacktraceTypeResolverSupplier.get();
        stacktraceTypeResolver.start(type);
        stacktraceTypeResolver.applyThread(eventThread);

        StacktraceEncoder stacktraceEncoder = new StacktraceEncoder();
        for (RecordedFrame recordedFrame : stacktrace.getFrames().reversed()) {
            RecordedMethod method = recordedFrame.getMethod();
            EventFrame eventFrame = new EventFrame(
                    method.getType().getName(),
                    method.getName(),
                    recordedFrame.getType(),
                    recordedFrame.getBytecodeIndex(),
                    recordedFrame.getLineNumber());

            stacktraceEncoder.addFrame(eventFrame);
            stacktraceTypeResolver.applyFrame(eventFrame);
        }

        StacktraceType stacktraceType = stacktraceTypeResolver.resolve();
        return new EventStacktrace(stacktraceType, stacktraceEncoder.build());
    }

    private static Set<StacktraceTag> resolveStacktraceTags(RecordedStackTrace stacktrace) {
        Set<StacktraceTag> tags = new HashSet<>();
        for (StacktraceTagResolver tagResolver : tagResolvers) {
            StacktraceTag tag = tagResolver.apply(stacktrace);
            if (tag != null) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private EventThread mapThread(RecordedThread thread) {
        long osThreadId = thread.getOSThreadId();
        long javaThreadId = thread.getJavaThreadId();
        String name = thread.getJavaName() != null ? thread.getJavaName() : thread.getOSName();
        if (name == null) {
            // it's a default format for threads without a name for AsyncProfiler
            // there is a mechanism to resolve the real name of the thread later
            name = "[tid=" + osThreadId + "]";
        }

        return new EventThread(
                name,
                osThreadId < 0 ? null : osThreadId,
                javaThreadId < 0 ? null : javaThreadId,
                thread.isVirtual());
    }

    @Override
    public void onComplete() {
        writer.onThreadComplete();
    }

    @Override
    public Void get() {
        return null;
    }

    private RecordedThread resolveThread(RecordedEvent event) {
        if (event.hasField("sampledThread")) {
            return event.getThread("sampledThread");
        } else {
            return event.getThread();
        }
    }

    private long calculateSamples(RecordedEvent event, Type eventType) {
        if (eventType != Type.WALL_CLOCK_SAMPLE) {
            return 1;
        } else {
            return event.getLong("samples");
        }
    }

    private Long calculateWeight(RecordedEvent event, Type eventType) {
        WeightExtractor weightExtractor = WeightExtractorRegistry.resolve(eventType);
        if (weightExtractor != null && weightExtractor.extractor() != null) {
            return weightExtractor.extractor().applyAsLong(event);
        } else {
            return null;
        }
    }

    private String retrieveWeightEntity(RecordedEvent event, Type eventType) {
        WeightExtractor weightExtractor = WeightExtractorRegistry.resolve(eventType);
        if (weightExtractor != null && weightExtractor.entityExtractor() != null) {
            return weightExtractor.entityExtractor().apply(event);
        } else {
            return null;
        }
    }
}
