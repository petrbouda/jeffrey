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
