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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.common.event.JITCompilerType;
import cafe.jeffrey.profile.common.event.JITDeoptimizationEvent;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Keeps the most recent {@code limit} jdk.Deoptimization events ordered by event timestamp descending.
 * Uses a min-heap keyed by timestamp so memory stays O(limit) regardless of total event count.
 */
public class JITDeoptimizationEventsBuilder
        implements RecordBuilder<GenericRecord, List<JITDeoptimizationEvent>> {

    private final int limit;
    private final PriorityQueue<JITDeoptimizationEvent> heap;

    public JITDeoptimizationEventsBuilder(int limit) {
        this.limit = limit;
        this.heap = new PriorityQueue<>(Comparator.comparingLong(JITDeoptimizationEvent::timestamp));
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();

        long timestamp = record.startTimestamp() != null ? record.startTimestamp().toEpochMilli() : 0L;
        String thread = record.thread() != null ? record.thread().name() : null;

        JITDeoptimizationEvent event = new JITDeoptimizationEvent(
                timestamp,
                thread,
                longField(fields, "compileId"),
                compilerOf(textField(fields, "compiler")),
                textField(fields, "method"),
                intField(fields, "lineNumber"),
                intField(fields, "bci"),
                textField(fields, "instruction"),
                textField(fields, "reason"),
                textField(fields, "action"));

        if (heap.size() < limit) {
            heap.add(event);
        } else if (heap.peek() != null && event.timestamp() > heap.peek().timestamp()) {
            heap.poll();
            heap.add(event);
        }
    }

    @Override
    public List<JITDeoptimizationEvent> build() {
        List<JITDeoptimizationEvent> result = new ArrayList<>(heap);
        result.sort(Comparator.comparingLong(JITDeoptimizationEvent::timestamp).reversed());
        return result;
    }

    private static String textField(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString(null);
    }

    private static long longField(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? 0L : value.asLong(0L);
    }

    private static int intField(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? 0 : value.asInt(0);
    }

    private static JITCompilerType compilerOf(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase()) {
            case "c1" -> JITCompilerType.C1;
            case "c2" -> JITCompilerType.C2;
            case "jvmci" -> JITCompilerType.JVMCI;
            default -> null;
        };
    }
}
