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

package pbouda.jeffrey.writer;

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
import pbouda.jeffrey.profile.settings.ActiveSetting;
import pbouda.jeffrey.profile.settings.ActiveSettingsProcessor;
import pbouda.jeffrey.profile.settings.SettingNameLabel;
import pbouda.jeffrey.profile.viewer.EventFieldsToJsonMapper;
import pbouda.jeffrey.writer.profile.BatchingDatabaseWriter;
import pbouda.jeffrey.writer.profile.ProfileSequences;
import pbouda.jeffrey.writer.stacktrace.StacktraceTypeResolver;
import pbouda.jeffrey.writer.stacktrace.StacktraceTypeResolverImpl;
import pbouda.jeffrey.writer.tag.IdleStacktraceTagResolver;
import pbouda.jeffrey.writer.tag.StacktraceTagResolver;
import pbouda.jeffrey.writer.tag.UnsafeAllocationStacktraceTagResolver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class DatabaseEventWriterProcessor implements EventProcessor<DatabaseWriterResult> {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseEventWriterProcessor.class);

    private static final List<StacktraceTagResolver> tagResolvers = List.of(
            new UnsafeAllocationStacktraceTagResolver(),
            new IdleStacktraceTagResolver()
    );

    private final ActiveSettingsProcessor activeSettingsProcessor = new ActiveSettingsProcessor();

    private final ProfileSequences sequences;
    private final BatchingDatabaseWriter<Event> eventWriter;
    private final BatchingDatabaseWriter<EventStacktrace> stacktraceWriter;
    private final BatchingDatabaseWriter<EventStacktraceTag> stacktraceTagWriter;

    private final MutableObjectLongMap<Type> weightCollector = ObjectLongMaps.mutable.empty();
    private final MutableObjectLongMap<Type> samplesCollector = ObjectLongMaps.mutable.empty();

    private final Map<RecordedStackTrace, EventStacktrace> stacktraces = new IdentityHashMap<>();
    private final Map<RecordedThread, EventThread> threads = new IdentityHashMap<>();

    private final Supplier<StacktraceTypeResolver> stacktraceTypeResolverSupplier = StacktraceTypeResolverImpl::new;

    private EventFieldsToJsonMapper eventFieldsToJsonMapper;
    private List<EventType> eventTypes;

    public DatabaseEventWriterProcessor(
            ProfileSequences sequences,
            BatchingDatabaseWriter<Event> eventWriter,
            BatchingDatabaseWriter<EventStacktrace> stacktraceWriter,
            BatchingDatabaseWriter<EventStacktraceTag> stacktraceTagWriter) {

        this.sequences = sequences;
        this.eventWriter = eventWriter;
        this.stacktraceWriter = stacktraceWriter;
        this.stacktraceTagWriter = stacktraceTagWriter;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public void onStart(List<EventType> eventTypes) {
        this.activeSettingsProcessor.onStart(eventTypes);
        this.eventTypes = eventTypes;
        this.eventFieldsToJsonMapper = new EventFieldsToJsonMapper(eventTypes);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        jdk.jfr.EventType eventType = event.getEventType();
        Type type = Type.from(eventType);

        if (type == Type.ACTIVE_SETTING) {
            activeSettingsProcessor.onEvent(event);
        }

        // All fields of the event are mapped to JSON
        ObjectNode eventFields = eventFieldsToJsonMapper.map(event);

        long samples = calculateSamples(event, type);
        Long weight = calculateWeight(event, type);

        if (weight != null) {
            weightCollector.addToValue(type, weight);
        }
        samplesCollector.addToValue(type, samples);

        /*
         * The thread is resolved based on the thread of the event.
         */
        EventThread eventThread = resolveThread(event);

        /*
         * Resolve stacktrace and cache it to deduplicate the same stacktraces.
         */
        RecordedStackTrace stackTrace = event.getStackTrace();
        EventStacktrace eventStacktrace = null;
        if (stackTrace != null) {
            eventStacktrace = stacktraces.computeIfAbsent(
                    stackTrace, st -> mapStacktrace(type, eventThread, st));
        }

        /*
         * The stacktrace tags are resolved based on the stacktrace of the event.
         */
        if (stackTrace != null) {
            List<EventStacktraceTag> eventStacktraceTags = mapStacktraceTags(stackTrace, eventStacktrace);
            eventStacktraceTags.forEach(stacktraceTagWriter::insert);
        }

        Duration duration = event.getDuration();
        Event newEvent = new Event(
                this.sequences.nextEventId(),
                eventType.getName(),
                event.getStartTime().toEpochMilli(),
                duration != Duration.ZERO ? duration.toNanos() : null,
                samples,
                weight,
                eventStacktrace != null ? eventStacktrace.stacktraceId() : null,
                eventThread != null ? eventThread.threadId() : null,
                eventFields
        );
        eventWriter.insert(newEvent);
        return Result.CONTINUE;
    }

    private EventStacktrace mapStacktrace(Type type, EventThread eventThread, RecordedStackTrace stacktrace) {
        StacktraceTypeResolver stacktraceTypeResolver = stacktraceTypeResolverSupplier.get();
        stacktraceTypeResolver.start(type);
        stacktraceTypeResolver.applyThread(eventThread);

        List<EventFrame> frames = new ArrayList<>();
        for (RecordedFrame recordedFrame : stacktrace.getFrames().reversed()) {
            RecordedMethod method = recordedFrame.getMethod();
            EventFrame eventFrame = new EventFrame(
                    method.getType().getName(),
                    method.getName(),
                    FrameType.fromCode(recordedFrame.getType()),
                    recordedFrame.getBytecodeIndex(),
                    recordedFrame.getLineNumber());

            frames.add(eventFrame);
            stacktraceTypeResolver.applyFrame(eventFrame);
        }

        StacktraceType stacktraceType = stacktraceTypeResolver.resolve();
        return new EventStacktrace(this.sequences.nextStacktraceId(), stacktraceType, frames);
    }

    private static List<EventStacktraceTag> mapStacktraceTags(
            RecordedStackTrace stacktrace, EventStacktrace eventStacktrace) {

        List<EventStacktraceTag> tags = new ArrayList<>();
        for (StacktraceTagResolver tagResolver : tagResolvers) {
            StacktraceTag tag = tagResolver.apply(stacktrace);
            if (tag != null) {
                tags.add(new EventStacktraceTag(eventStacktrace, tag));
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
                this.sequences.nextThreadId(),
                name,
                osThreadId < 0 ? null : osThreadId,
                javaThreadId < 0 ? null : javaThreadId,
                thread.isVirtual());
    }

    @Override
    public void onComplete() {
        try {
            stacktraceWriter.start();
            stacktraceTagWriter.start();

            stacktraces.values().forEach(stacktraceWriter::insert);

            eventWriter.close();
            stacktraceWriter.close();
            stacktraceTagWriter.close();
        } catch (Exception e) {
            throw new RuntimeException("Cannot finish writing events to Database", e);
        }
    }

    @Override
    public DatabaseWriterResult get() {
        Map<SettingNameLabel, ActiveSetting> activeSettings = activeSettingsProcessor.get();
        return new DatabaseWriterResult(
                List.copyOf(threads.values()), eventTypes, samplesCollector, weightCollector, activeSettings);
    }

    private EventThread resolveThread(RecordedEvent event) {
        RecordedThread thread;
        if (event.hasField("sampledThread")) {
            thread = event.getThread("sampledThread");
        } else {
            thread = event.getThread();
        }

        if (thread != null) {
            return threads.computeIfAbsent(thread, this::mapThread);
        } else {
            return null;
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
        if (eventType.isWeightSupported()) {
            return eventType.weight().extractor().applyAsLong(event);
        } else {
            return null;
        }
    }
}
