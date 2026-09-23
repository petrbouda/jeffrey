/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.microscope.model.StacktraceTag;
import cafe.jeffrey.microscope.model.settings.ActiveSetting;
import cafe.jeffrey.provider.profile.api.EventWriters;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.provider.profile.api.*;
import cafe.jeffrey.provider.profile.api.EventDeduplicator;
import cafe.jeffrey.provider.profile.api.EventFrameWithHash;
import cafe.jeffrey.provider.profile.api.EventStacktraceWithHash;
import cafe.jeffrey.provider.profile.api.EventThreadWithHash;

import java.time.Instant;
import java.util.*;

public class SQLSingleThreadedEventWriter implements SingleThreadedEventWriter {

    private final SingleThreadHasher hasher = new SingleThreadHasher();

    private final Map<String, ActiveSetting> activeSettings = new HashMap<>();
    private final List<EventThreadWithHash> eventThreads = new ArrayList<>();
    private final List<EventType> eventTypes = new ArrayList<>();
    private final EventWriters writersProvider;
    private final EventDeduplicator deduplicator;
    private final Set<String> eventTypesContainingStacktraces = new HashSet<>();

    private Instant latestEventTimestamp = Instant.MIN;

    public SQLSingleThreadedEventWriter(EventWriters writersProvider, EventDeduplicator deduplicator) {
        this.writersProvider = writersProvider;
        this.deduplicator = deduplicator;
    }

    @Override
    public void onEvent(Event event) {
        writersProvider.events().insert(event);

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
        // Only the frame hashes are needed to decide whether the stacktrace is a duplicate,
        // the EventFrameWithHash objects are built only for new stacktraces below.
        List<EventFrame> frames = stacktrace.frames();
        long[] stacktraceFrameHashes = new long[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            stacktraceFrameHashes[i] = hasher.hashFrame(frames.get(i));
        }

        long stacktraceHash = hasher.hashStackTrace(stacktraceFrameHashes);
        if (deduplicator.checkAndAddStacktrace(stacktraceHash)) {
            EventStacktraceWithHash stacktraceWithHash = new EventStacktraceWithHash(
                    stacktraceHash,
                    stacktraceFrameHashes,
                    stacktrace.type(),
                    toStacktraceTagsArray(stacktrace.tags()));

            writersProvider.stacktraces().insert(stacktraceWithHash);

            List<EventFrameWithHash> deduplicatedFrames = new ArrayList<>();
            for (int i = 0; i < frames.size(); i++) {
                long frameHash = stacktraceFrameHashes[i];
                if (deduplicator.checkAndAddFrame(frameHash)) {
                    deduplicatedFrames.add(new EventFrameWithHash(frameHash, frames.get(i)));
                }
            }

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
    public long onFieldText(String text) {
        long hash = hasher.hashText(text);
        if (deduplicator.checkAndAddFieldText(hash)) {
            writersProvider.fieldTexts().insert(new FieldTextWithHash(hash, text));
        }
        return hash;
    }

    @Override
    public long onEventThread(EventThread thread) {
        long hash = hasher.hashThread(thread);
        if (deduplicator.checkAndAddThread(hash)) {
            eventThreads.add(new EventThreadWithHash(hash, thread));
        }
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
        List<EventTypeBuilder> builders = eventTypes.stream()
                .map(EventTypeBuilder::newBuilder)
                .toList();

        return new EventWriterResult(
                eventThreads,
                builders,
                activeSettings,
                eventTypesContainingStacktraces,
                latestEventTimestamp);
    }
}
