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

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the distinct network-interface names seen in {@code jdk.NetworkUtilization} events,
 * sorted alphabetically.
 */
public class NetworkInterfacesBuilder implements RecordBuilder<GenericRecord, List<String>> {

    private static final String NETWORK_INTERFACE_FIELD = "networkInterface";

    private final Set<String> interfaces = new LinkedHashSet<>();

    @Override
    public void onRecord(GenericRecord record) {
        String name = Json.readString(record.jsonFields(), NETWORK_INTERFACE_FIELD);
        if (name != null && !name.isBlank()) {
            interfaces.add(name);
        }
    }

    @Override
    public List<String> build() {
        List<String> result = new ArrayList<>(interfaces);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
