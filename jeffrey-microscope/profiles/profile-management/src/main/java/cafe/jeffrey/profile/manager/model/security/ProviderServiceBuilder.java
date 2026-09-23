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
