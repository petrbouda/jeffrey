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
