/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Tallies what reached the writer, by event type: how many events, and how many samples they carry.
 * Ids are handed out per writer and a parse uses one writer per unit, so they differ between runs by
 * construction — the tallies are what two readings of the same recording have to agree on.
 */
final class EventTally implements EventWriter {

    private final Map<String, LongAdder> events = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> samples = new ConcurrentHashMap<>();

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        return new SingleThreadedEventWriter() {
            @Override
            public void onEvent(Event event) {
                events.computeIfAbsent(event.eventType(), _ -> new LongAdder()).increment();
                samples.computeIfAbsent(event.eventType(), _ -> new LongAdder()).add(event.samples());
            }

            /** Takes a pooled field text, as a writer that stores it does, and keeps nothing of it. */
            @Override
            public long onFieldText(String text) {
                return text.hashCode();
            }
        };
    }

    @Override
    public void onComplete() {
    }

    /** Events by type. */
    Map<String, Long> events() {
        return snapshot(events);
    }

    /** Samples by type: the events' {@code samples} fields summed. */
    Map<String, Long> samples() {
        return snapshot(samples);
    }

    long events(String eventType) {
        return events().getOrDefault(eventType, 0L);
    }

    long samples(String eventType) {
        return samples().getOrDefault(eventType, 0L);
    }

    private static Map<String, Long> snapshot(Map<String, LongAdder> tally) {
        return tally.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().sum()));
    }
}
