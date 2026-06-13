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
