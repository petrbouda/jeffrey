/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import cafe.jeffrey.shared.common.BytesUtils;
import cafe.jeffrey.shared.common.DurationUtils;

import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

public record WeightExtractor(
        ToLongFunction<RecordedEvent> extractor,
        LongFunction<String> formatter,
        Function<RecordedEvent, String> entityExtractor) {

    public static WeightExtractor duration() {
        return new WeightExtractor(
                e -> e.getDuration().toNanos(),
                DurationUtils::formatNanos,
                null);
    }

    /**
     * Weight by a named {@code @Timespan} field (in nanos) rather than the event's own duration —
     * e.g. {@code jdk.CPUTimeSample.samplingPeriod}, which carries the per-sample on-CPU time.
     */
    public static WeightExtractor durationField(String fieldName) {
        return new WeightExtractor(
                e -> e.getDuration(fieldName).toNanos(),
                DurationUtils::formatNanos,
                null);
    }

    public static WeightExtractor duration(String entityClassField) {
        return new WeightExtractor(
                e -> e.getDuration().toNanos(),
                DurationUtils::formatNanos,
                classNameOf(entityClassField));
    }

    public static WeightExtractor duration(Function<RecordedEvent, String> entityExtractor) {
        return new WeightExtractor(
                e -> e.getDuration().toNanos(),
                DurationUtils::formatNanos,
                entityExtractor);
    }

    public static WeightExtractor duration(String fieldName, Function<RecordedEvent, String> entityExtractor) {
        return new WeightExtractor(
                e -> e.getLong(fieldName),
                BytesUtils::format,
                entityExtractor);
    }

    public static WeightExtractor allocation(String fieldName) {
        return allocation(fieldName, (Function<RecordedEvent, String>) null);
    }

    public static WeightExtractor allocation(String fieldName, String entityClassField) {
        return new WeightExtractor(
                e -> e.getLong(fieldName),
                BytesUtils::format,
                classNameOf(entityClassField));
    }

    public static WeightExtractor allocation(String fieldName, Function<RecordedEvent, String> entityExtractor) {
        return new WeightExtractor(
                e -> e.getLong(fieldName),
                BytesUtils::format,
                entityExtractor);
    }

    public static WeightExtractor allocationEntityOnly(Function<RecordedEvent, String> entityExtractor) {
        return new WeightExtractor(null, null, entityExtractor);
    }

    /**
     * Reads the name of a class-typed field. The field is declared on the event type but is not always
     * populated — {@code jdk.ThreadPark} carries no {@code parkedClass} when the code parks without a
     * blocker, and {@code jdk.JavaMonitorWait} can omit {@code monitorClass} — so an absent class means
     * the event has no weight entity rather than a broken recording.
     */
    private static Function<RecordedEvent, String> classNameOf(String entityClassField) {
        return event -> {
            RecordedClass entityClass = event.getClass(entityClassField);
            if (entityClass == null) {
                return null;
            }
            return entityClass.getName();
        };
    }
}
