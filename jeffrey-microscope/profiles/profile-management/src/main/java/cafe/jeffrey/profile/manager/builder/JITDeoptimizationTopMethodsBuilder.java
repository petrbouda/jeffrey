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
import cafe.jeffrey.profile.common.event.JITDeoptimizationMethodAggregate;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates jdk.Deoptimization events by method, returning the top {@code limit} methods by event count.
 */
public class JITDeoptimizationTopMethodsBuilder
        implements RecordBuilder<GenericRecord, List<JITDeoptimizationMethodAggregate>> {

    private static final class Aggregate {
        long count;
        final Map<String, Long> reasons = new HashMap<>();
        final EnumSet<JITCompilerType> compilers = EnumSet.noneOf(JITCompilerType.class);
        long firstTimestamp = Long.MAX_VALUE;
        long lastTimestamp = Long.MIN_VALUE;
    }

    private final int limit;
    private final Map<String, Aggregate> perMethod = new LinkedHashMap<>();

    public JITDeoptimizationTopMethodsBuilder(int limit) {
        this.limit = limit;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();

        String method = textField(fields, "method");
        if (method == null) {
            return;
        }

        Aggregate aggregate = perMethod.computeIfAbsent(method, k -> new Aggregate());
        aggregate.count++;

        String reason = textField(fields, "reason");
        if (reason != null) {
            aggregate.reasons.merge(reason, 1L, Long::sum);
        }

        JITCompilerType compiler = compilerOf(textField(fields, "compiler"));
        if (compiler != null) {
            aggregate.compilers.add(compiler);
        }

        long timestamp = record.startTimestamp() != null ? record.startTimestamp().toEpochMilli() : 0L;
        if (timestamp < aggregate.firstTimestamp) aggregate.firstTimestamp = timestamp;
        if (timestamp > aggregate.lastTimestamp) aggregate.lastTimestamp = timestamp;
    }

    @Override
    public List<JITDeoptimizationMethodAggregate> build() {
        List<Map.Entry<String, Aggregate>> sorted = new ArrayList<>(perMethod.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Aggregate>>comparingLong(e -> e.getValue().count).reversed());

        int max = Math.min(limit, sorted.size());
        List<JITDeoptimizationMethodAggregate> result = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            Map.Entry<String, Aggregate> entry = sorted.get(i);
            Aggregate aggregate = entry.getValue();

            Map.Entry<String, Long> dominant = top(aggregate.reasons);
            result.add(new JITDeoptimizationMethodAggregate(
                    entry.getKey(),
                    aggregate.count,
                    aggregate.reasons.size(),
                    dominant == null ? null : dominant.getKey(),
                    dominant == null ? 0 : dominant.getValue(),
                    new ArrayList<>(aggregate.compilers),
                    aggregate.firstTimestamp == Long.MAX_VALUE ? 0 : aggregate.firstTimestamp,
                    aggregate.lastTimestamp == Long.MIN_VALUE ? 0 : aggregate.lastTimestamp));
        }
        return result;
    }

    private static String textField(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString(null);
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

    private static Map.Entry<String, Long> top(Map<String, Long> counts) {
        Map.Entry<String, Long> best = null;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (best == null || entry.getValue() > best.getValue()) {
                best = entry;
            }
        }
        return best;
    }
}
