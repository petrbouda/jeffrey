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

package cafe.jeffrey.profile.manager.model.system;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects module dependency edges from {@code jdk.ModuleRequire}, de-duplicated, sorted by source then
 * required module. The {@code source}/{@code requiredModule} struct fields are flattened to module names
 * by the event-to-JSON mapper.
 */
public class ModuleRequiresBuilder implements RecordBuilder<GenericRecord, List<ModuleEdge>> {

    private static final String SOURCE_FIELD = "source";
    private static final String REQUIRED_MODULE_FIELD = "requiredModule";

    private final Set<ModuleEdge> edges = new LinkedHashSet<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        edges.add(new ModuleEdge(
                Json.readString(fields, SOURCE_FIELD),
                Json.readString(fields, REQUIRED_MODULE_FIELD)));
    }

    @Override
    public List<ModuleEdge> build() {
        List<ModuleEdge> result = new ArrayList<>(edges);
        result.sort(Comparator
                .comparing(ModuleEdge::source, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ModuleEdge::required, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }
}
