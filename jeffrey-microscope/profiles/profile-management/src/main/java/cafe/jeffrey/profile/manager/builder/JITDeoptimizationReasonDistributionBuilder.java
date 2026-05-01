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
import cafe.jeffrey.profile.common.event.JITDeoptimizationReasonCount;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JITDeoptimizationReasonDistributionBuilder
        implements RecordBuilder<GenericRecord, List<JITDeoptimizationReasonCount>> {

    private final Map<String, Long> reasonCounts = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        JsonNode reason = fields.get("reason");
        if (reason == null || reason.isNull()) {
            return;
        }
        reasonCounts.merge(reason.asString(""), 1L, Long::sum);
    }

    @Override
    public List<JITDeoptimizationReasonCount> build() {
        List<JITDeoptimizationReasonCount> result = new ArrayList<>(reasonCounts.size());
        reasonCounts.forEach((k, v) -> result.add(new JITDeoptimizationReasonCount(k, v)));
        result.sort(Comparator.comparingLong(JITDeoptimizationReasonCount::count).reversed());
        return result;
    }
}
