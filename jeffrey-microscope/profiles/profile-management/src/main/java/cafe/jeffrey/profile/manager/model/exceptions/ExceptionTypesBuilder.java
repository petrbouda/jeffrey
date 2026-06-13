/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.exceptions;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups {@code jdk.JavaExceptionThrow} / {@code jdk.JavaErrorThrow} events by thrown class,
 * collecting per-class counts, every distinct message with its occurrence count, and the distinct
 * throwing threads. The result is ordered by descending throw count.
 */
public class ExceptionTypesBuilder implements RecordBuilder<GenericRecord, List<ExceptionTypeStat>> {

    private static final String THROWN_CLASS_FIELD = "thrownClass";
    private static final String MESSAGE_FIELD = "message";
    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String UNKNOWN_CLASS = "<unknown>";

    private static final class Accumulator {
        private long count;
        private boolean error;
        private final Map<String, Long> messageCounts = new HashMap<>();
        private final Set<String> threads = new LinkedHashSet<>();
    }

    private final Map<String, Accumulator> accumulatorsByClass = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String thrownClass = Json.readString(fields, THROWN_CLASS_FIELD);
        if (thrownClass == null) {
            thrownClass = UNKNOWN_CLASS;
        }

        Accumulator accumulator = accumulatorsByClass.computeIfAbsent(thrownClass, key -> new Accumulator());
        accumulator.count++;
        if (Type.JAVA_ERROR_THROW.equals(record.type())) {
            accumulator.error = true;
        }

        String message = Json.readString(fields, MESSAGE_FIELD);
        if (message != null && !message.isBlank()) {
            accumulator.messageCounts.merge(message, 1L, Long::sum);
        }
        String thread = Json.readString(fields, EVENT_THREAD_FIELD);
        if (thread != null) {
            accumulator.threads.add(thread);
        }
    }

    @Override
    public List<ExceptionTypeStat> build() {
        List<ExceptionTypeStat> result = new ArrayList<>(accumulatorsByClass.size());
        accumulatorsByClass.forEach((thrownClass, accumulator) -> result.add(new ExceptionTypeStat(
                thrownClass,
                accumulator.count,
                accumulator.error,
                toMessageCounts(accumulator.messageCounts),
                accumulator.threads.size())));
        result.sort(Comparator.comparingLong(ExceptionTypeStat::count).reversed());
        return result;
    }

    private static List<ExceptionMessageCount> toMessageCounts(Map<String, Long> messageCounts) {
        List<ExceptionMessageCount> messages = new ArrayList<>(messageCounts.size());
        messageCounts.forEach((message, count) -> messages.add(new ExceptionMessageCount(message, count)));
        messages.sort(Comparator.comparingLong(ExceptionMessageCount::count).reversed()
                .thenComparing(ExceptionMessageCount::message));
        return messages;
    }
}
