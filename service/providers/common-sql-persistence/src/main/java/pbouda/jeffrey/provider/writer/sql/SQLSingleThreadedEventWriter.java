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

package pbouda.jeffrey.provider.writer.sql;

import org.eclipse.collections.api.factory.primitive.ObjectLongMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectLongMap;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.settings.ActiveSetting;
import pbouda.jeffrey.provider.api.EventWriters;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.api.model.*;
import pbouda.jeffrey.provider.api.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceWithHash;
import pbouda.jeffrey.provider.api.model.writer.EventThreadWithHash;
import pbouda.jeffrey.provider.api.model.writer.LongDeduplicator;

import java.time.Instant;
import java.util.*;

public class SQLSingleThreadedEventWriter implements SingleThreadedEventWriter {

    private final SingleThreadHasher hasher = new SingleThreadHasher();

    private final MutableObjectLongMap<String> weightCollector = ObjectLongMaps.mutable.empty();
    private final MutableObjectLongMap<String> samplesCollector = ObjectLongMaps.mutable.empty();
    private final Map<String, ActiveSetting> activeSettings = new HashMap<>();
    private final List<EventThreadWithHash> eventThreads = new ArrayList<>();
    private final List<EventType> eventTypes = new ArrayList<>();
    private final EventWriters writersProvider;
    private final Set<String> eventTypesContainingStacktraces = new HashSet<>();

    private final LongDeduplicator frameDeduplicator = new LongDeduplicator();
    private final LongDeduplicator stacktraceDeduplicator = new LongDeduplicator();

    private Instant latestEventTimestamp = Instant.MIN;

    public SQLSingleThreadedEventWriter(EventWriters writersProvider) {
        this.writersProvider = writersProvider;
    }

    @Override
    public void onThreadStart() {
    }

    @Override
    public void onEvent(Event event) {
        writersProvider.events().insert(event);
        if (event.weight() != null) {
            weightCollector.addToValue(event.eventType(), event.weight());
        }
        samplesCollector.addToValue(event.eventType(), event.samples());

        if (event.startTimestamp().isAfter(latestEventTimestamp)) {
            latestEventTimestamp = event.startTimestamp();
        }
        if (event.stacktraceId() != null) {
            eventTypesContainingStacktraces.add(event.eventType());
        }
    }

    @Override
    public void onEventSetting(EventSetting eventSetting) {
        String eventType = eventSetting.eventType();
        ActiveSetting setting = activeSettings.get(eventType);
        if (setting == null) {
            setting = new ActiveSetting(eventType);
            activeSettings.put(eventType, setting);
        }
        setting.putParam(eventSetting.name(), eventSetting.value());
    }

    @Override
    public void onEventType(EventType eventType) {
        eventTypes.add(eventType);
    }

    @Override
    public long onEventStacktrace(EventStacktrace stacktrace) {
        List<EventFrameWithHash> framesWithHash = new ArrayList<>();
        long[] stacktraceFrameHashes = new long[stacktrace.frames().size()];
        for (int i = 0; i < stacktrace.frames().size(); i++) {
            EventFrame frame = stacktrace.frames().get(i);
            long frameHash = hasher.hashFrame(frame);
            framesWithHash.add(new EventFrameWithHash(frameHash, frame));
            stacktraceFrameHashes[i] = frameHash;
        }

        long stacktraceHash = hasher.hashStackTrace(stacktraceFrameHashes);
        if (stacktraceDeduplicator.checkAndAdd(stacktraceHash)) {
            EventStacktraceWithHash stacktraceWithHash = new EventStacktraceWithHash(
                    stacktraceHash,
                    stacktraceFrameHashes,
                    stacktrace.type(),
                    toStacktraceTagsArray(stacktrace.tags()));

            writersProvider.stacktraces().insert(stacktraceWithHash);

            List<EventFrameWithHash> deduplicatedFrames = framesWithHash.stream()
                    .filter(frame -> frameDeduplicator.checkAndAdd(frame.hash()))
                    .toList();

            if (!deduplicatedFrames.isEmpty()) {
                writersProvider.frames().insertBatch(deduplicatedFrames);
            }
        }
        return stacktraceHash;
    }

    private static int[] toStacktraceTagsArray(Set<StacktraceTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return new int[0];
        }
        return tags.stream().mapToInt(StacktraceTag::id).toArray();
    }

    @Override
    public long onEventThread(EventThread thread) {
        long hash = hasher.hashThread(thread);
        eventThreads.add(new EventThreadWithHash(hash, thread));
        return hash;
    }

    @Override
    public void onThreadComplete() {
        try {
            writersProvider.close();
        } catch (Exception e) {
            throw new RuntimeException("Cannot close Writers", e);
        }
    }

    public EventWriterResult getResult() {
        List<EventTypeBuilder> eventTypeBuilders = new ArrayList<>();
        for (EventType eventType : eventTypes) {
            EventTypeBuilder builder = EventTypeBuilder.newBuilder(eventType)
                    .addSamples(samplesCollector.get(eventType.name()))
                    .addWeight(weightCollector.get(eventType.name()));

            eventTypeBuilders.add(builder);
        }

        return new EventWriterResult(
                eventThreads,
                eventTypeBuilders,
                activeSettings,
                eventTypesContainingStacktraces,
                latestEventTimestamp);
    }
}
