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
import pbouda.jeffrey.common.EventSummary;

import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

public class EventTypeCollector {

    private static final ToLongFunction<RecordedEvent> NOOP_WEIGHT_EXTRACTOR = event -> 0L;

    private final EventType eventType;
    private final ToLongFunction<RecordedEvent> weightExtractor;

    private long samples = 0L;
    private long weight = 0L;

    public EventTypeCollector(EventType eventType) {
        this(eventType, NOOP_WEIGHT_EXTRACTOR);
    }

    public EventTypeCollector(EventType eventType, ToLongFunction<RecordedEvent> weightExtractor) {
        this.eventType = eventType;
        this.weightExtractor = weightExtractor;
    }

    public void incrementSamples(int samples) {
        this.samples += samples;
    }

    public void incrementWeight(RecordedEvent event) {
        this.weight += weightExtractor.applyAsLong(event);
    }

    public EventSummary buildSummary() {
        return new EventSummary(
                eventType.getName(),
                eventType.getLabel(),
                samples,
                weightExtractor == null ? -1 : weight,
                containsStackTrace(eventType),
                eventType.getCategoryNames(),
                Map.of());
    }

    private static boolean containsStackTrace(EventType event) {
        return event.getField("stackTrace") != null;
    }

    public EventTypeCollector merge(EventTypeCollector other) {
        this.samples += other.samples;
        this.weight += other.weight;
        return this;
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
