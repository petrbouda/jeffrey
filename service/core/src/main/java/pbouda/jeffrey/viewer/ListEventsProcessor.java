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

package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.SingleEventProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class ListEventsProcessor extends SingleEventProcessor<ArrayNode> {

    private final ArrayNode result = Json.createArray();
    private final List<String> ignoredFields;

    public ListEventsProcessor(Type eventType, List<String> ignoredFields) {
        super(eventType);
        this.ignoredFields = ignoredFields;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ObjectNode node = Json.createObject();
        for (ValueDescriptor field : event.getFields()) {
            if (!ignoredFields.contains(field.getName())) {
                if ("long".equals(field.getTypeName()) && "jdk.jfr.Timestamp".equals(field.getContentType())) {
                    Instant instant = event.getInstant(field.getName());
                    node.put(field.getName(), safeToLongMillis(instant));
                } else if ("jdk.jfr.Percentage".equals(field.getContentType())) {
                    float value = event.getFloat(field.getName());
                    node.put(field.getName(), value);
                } else if ("jdk.jfr.Timespan".equals(field.getContentType())) {
                    Duration value = event.getDuration(field.getName());
                    node.put(field.getName(), safeDurationToLongNanos(value));
                } else if ("java.lang.Thread".equals(field.getTypeName())) {
                    RecordedThread value = event.getThread(field.getName());
                    node.put(field.getName(), safeThreadToString(value));
                } else if ("java.lang.Class".equals(field.getTypeName())) {
                    RecordedClass clazz = event.getClass(field.getName());
                    node.put(field.getName(), RecordedClassMapper.map(clazz));
                } else if ("jdk.types.Method".equals(field.getTypeName())) {
                    RecordedMethod method = event.getValue(field.getName());
                    node.put(field.getName(), method.getType().getName() + "#" + method.getName());
                } else {
                    String value = safeToString(event.getValue(field.getName()));
                    node.put(field.getName(), value);
                }
            }
        }
        result.add(node);
        return Result.CONTINUE;
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    private static long safeToLongNanos(Duration value) {
        return value.isNegative() ? -1 : value.toNanos();
    }

    private static long safeDurationToLongNanos(Duration value) {
        if (value.getSeconds() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        } else {
            return safeToLongNanos(value);
        }
    }

    private static long safeToLongMillis(Instant value) {
        return value == Instant.MIN ? 0 : value.toEpochMilli();
    }

    private static String safeThreadToString(RecordedThread value) {
        return value == null ? "" : value.getJavaName();
    }

    @Override
    public ArrayNode get() {
        return result;
    }
}
