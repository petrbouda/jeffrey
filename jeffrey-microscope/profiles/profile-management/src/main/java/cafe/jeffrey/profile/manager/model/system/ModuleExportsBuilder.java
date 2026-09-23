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
 * Collects package exports from {@code jdk.ModuleExport}, de-duplicated, sorted by package then target.
 * The {@code exportedPackage} (Package) and {@code targetModule} (Module) struct fields are flattened to
 * names by the event-to-JSON mapper; a {@code null} target means an unqualified export.
 */
public class ModuleExportsBuilder implements RecordBuilder<GenericRecord, List<ModuleExport>> {

    private static final String EXPORTED_PACKAGE_FIELD = "exportedPackage";
    private static final String TARGET_MODULE_FIELD = "targetModule";

    private final Set<ModuleExport> exports = new LinkedHashSet<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        exports.add(new ModuleExport(
                Json.readString(fields, EXPORTED_PACKAGE_FIELD),
                Json.readString(fields, TARGET_MODULE_FIELD)));
    }

    @Override
    public List<ModuleExport> build() {
        List<ModuleExport> result = new ArrayList<>(exports);
        result.sort(Comparator
                .comparing(ModuleExport::packageName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ModuleExport::targetModule, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }
}
