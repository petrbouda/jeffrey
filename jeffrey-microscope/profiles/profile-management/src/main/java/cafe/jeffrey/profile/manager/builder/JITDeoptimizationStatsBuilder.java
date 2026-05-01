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
import cafe.jeffrey.profile.common.event.JITDeoptimizationStats;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JITDeoptimizationStatsBuilder implements RecordBuilder<GenericRecord, JITDeoptimizationStats> {

    private final long recordingDurationMillis;
    private final Map<String, Long> reasonCounts = new HashMap<>();
    private final Map<String, Long> methodCounts = new HashMap<>();
    private final Set<String> distinctMethodSet = new HashSet<>();
    private long total;
    private long c1Count;
    private long c2Count;

    public JITDeoptimizationStatsBuilder(long recordingDurationMillis) {
        this.recordingDurationMillis = recordingDurationMillis;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        total++;

        String reason = textOrEmpty(fields, "reason");
        if (!reason.isEmpty()) {
            reasonCounts.merge(reason, 1L, Long::sum);
        }

        String method = textOrEmpty(fields, "method");
        if (!method.isEmpty()) {
            distinctMethodSet.add(method);
            methodCounts.merge(method, 1L, Long::sum);
        }

        String compiler = textOrEmpty(fields, "compiler");
        if ("c1".equalsIgnoreCase(compiler)) {
            c1Count++;
        } else if ("c2".equalsIgnoreCase(compiler)) {
            c2Count++;
        }
    }

    @Override
    public JITDeoptimizationStats build() {
        if (total == 0) {
            return new JITDeoptimizationStats(
                    0, 0, 0, null, 0, null, 0, 0, 0, recordingDurationMillis);
        }

        Map.Entry<String, Long> topReason = top(reasonCounts);
        Map.Entry<String, Long> topMethod = top(methodCounts);

        return new JITDeoptimizationStats(
                total,
                distinctMethodSet.size(),
                reasonCounts.size(),
                topReason == null ? null : topReason.getKey(),
                topReason == null ? 0 : topReason.getValue(),
                topMethod == null ? null : topMethod.getKey(),
                topMethod == null ? 0 : topMethod.getValue(),
                c1Count,
                c2Count,
                recordingDurationMillis);
    }

    private static String textOrEmpty(ObjectNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asString("");
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
