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

package cafe.jeffrey.profile.manager.model.security;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.security.SecurityData.ProviderServiceStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.SecurityProviderService} events into provider/type/algorithm usage counts —
 * which JCA providers and algorithms the application actually exercised.
 */
public class ProviderServiceBuilder implements RecordBuilder<GenericRecord, List<ProviderServiceStat>> {

    private static final String PROVIDER_FIELD = "provider";
    private static final String TYPE_FIELD = "type";
    private static final String ALGORITHM_FIELD = "algorithm";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_ROWS = 200;

    private record Key(String provider, String type, String algorithm) {
    }

    private final Map<Key, Long> counts = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        Key key = new Key(
                orUnknown(Json.readString(fields, PROVIDER_FIELD)),
                orUnknown(Json.readString(fields, TYPE_FIELD)),
                orUnknown(Json.readString(fields, ALGORITHM_FIELD)));
        counts.merge(key, 1L, Long::sum);
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    @Override
    public List<ProviderServiceStat> build() {
        return counts.entrySet().stream()
                .map(entry -> new ProviderServiceStat(
                        entry.getKey().provider(), entry.getKey().type(), entry.getKey().algorithm(),
                        entry.getValue()))
                .sorted(Comparator.comparingLong(ProviderServiceStat::count).reversed())
                .limit(MAX_ROWS)
                .toList();
    }
}
