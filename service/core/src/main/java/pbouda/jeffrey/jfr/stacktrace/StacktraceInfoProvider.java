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

package pbouda.jeffrey.jfr.stacktrace;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;

public class StacktraceInfoProvider implements Supplier<ArrayNode> {

    private static final String STACKTRACE_FIELD_NAME = "jdk.types.StackTrace";

    private final Path recording;

    public StacktraceInfoProvider(Path recording) {
        this.recording = recording;
    }

    /**
     * [{
     * index: 0,
     * label: 'Execution Samples (CPU)',
     * code: 'jdk.ExecutionSample'
     * },{ ... }]
     */
    @Override
    public ArrayNode get() {
        Set<EventType> eventTypes = new RecordingFileIterator<>(
                recording, new EventSupportStackTraceProcessor())
                .collect();

        int index = 0;
        ArrayNode result = Json.createArray();
        for (EventType eventType : eventTypes) {
            if (containsStackTrace(eventType)) {
                result.add(createEventDescription(index++, eventType));
            }
        }

        return result;
    }

    private static ObjectNode createEventDescription(int i, EventType eventType) {
        return Json.createObject()
                .put("index", i)
                .put("label", eventType.getLabel())
                .put("code", eventType.getName());
    }


    private static boolean containsStackTrace(EventType eventType) {
        for (ValueDescriptor field : eventType.getFields()) {
            if (STACKTRACE_FIELD_NAME.equals(field.getTypeName())) {
                return true;
            }
        }
        return false;
    }
}
