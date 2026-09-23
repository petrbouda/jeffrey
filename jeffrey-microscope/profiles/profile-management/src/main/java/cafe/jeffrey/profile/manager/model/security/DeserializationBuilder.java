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
import cafe.jeffrey.profile.manager.model.security.SecurityData.DeserializationSummary;
import cafe.jeffrey.profile.manager.model.security.SecurityData.DeserializationTypeStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.Deserialization} events: top types by total bytes read, plus a summary of
 * filter configuration / rejections / exceptions. Surfaces oversized object graphs and unfiltered
 * deserialization (a security risk).
 */
public class DeserializationBuilder implements RecordBuilder<GenericRecord, DeserializationBuilder.Result> {

    public record Result(DeserializationSummary summary, List<DeserializationTypeStat> types) {
    }

    private static final String TYPE_FIELD = "type";
    private static final String FILTER_CONFIGURED_FIELD = "filterConfigured";
    private static final String FILTER_STATUS_FIELD = "filterStatus";
    private static final String BYTES_READ_FIELD = "bytesRead";
    private static final String DEPTH_FIELD = "depth";
    private static final String EXCEPTION_TYPE_FIELD = "exceptionType";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String UNKNOWN_TYPE = "unknown";
    private static final int MAX_TYPES = 100;

    private static final class TypeAcc {
        private long count;
        private long totalBytes;
        private long maxBytes;
        private long maxDepth;
    }

    private final Map<String, TypeAcc> byType = new HashMap<>();
    private long totalEvents;
    private long filterConfiguredEvents;
    private long rejectedEvents;
    private long exceptionEvents;

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        totalEvents++;

        if (Json.readBoolean(fields, FILTER_CONFIGURED_FIELD)) {
            filterConfiguredEvents++;
        }
        if (REJECTED_STATUS.equalsIgnoreCase(Json.readString(fields, FILTER_STATUS_FIELD))) {
            rejectedEvents++;
        }
        String exceptionType = Json.readString(fields, EXCEPTION_TYPE_FIELD);
        if (exceptionType != null && !exceptionType.isBlank()) {
            exceptionEvents++;
        }

        String type = Json.readString(fields, TYPE_FIELD);
        long bytes = Math.max(0, Json.readLong(fields, BYTES_READ_FIELD));
        long depth = Math.max(0, Json.readLong(fields, DEPTH_FIELD));
        TypeAcc acc = byType.computeIfAbsent(type == null ? UNKNOWN_TYPE : type, key -> new TypeAcc());
        acc.count++;
        acc.totalBytes += bytes;
        acc.maxBytes = Math.max(acc.maxBytes, bytes);
        acc.maxDepth = Math.max(acc.maxDepth, depth);
    }

    @Override
    public Result build() {
        List<DeserializationTypeStat> types = byType.entrySet().stream()
                .map(entry -> new DeserializationTypeStat(
                        entry.getKey(), entry.getValue().count, entry.getValue().totalBytes,
                        entry.getValue().maxBytes, entry.getValue().maxDepth))
                .sorted(Comparator.comparingLong(DeserializationTypeStat::totalBytes).reversed())
                .limit(MAX_TYPES)
                .toList();

        return new Result(
                new DeserializationSummary(totalEvents, filterConfiguredEvents, rejectedEvents, exceptionEvents),
                types);
    }
}
