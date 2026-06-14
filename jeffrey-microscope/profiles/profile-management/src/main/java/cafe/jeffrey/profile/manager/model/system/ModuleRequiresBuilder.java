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
