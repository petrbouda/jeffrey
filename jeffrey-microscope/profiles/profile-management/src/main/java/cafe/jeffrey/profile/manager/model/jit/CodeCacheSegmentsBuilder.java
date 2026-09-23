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

package cafe.jeffrey.profile.manager.model.jit;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData.CodeCacheSegment;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses periodic {@code jdk.CodeCacheStatistics} snapshots into one row per code heap
 * (keyed by {@code codeBlobType}, last snapshot wins), ordered by descending used bytes.
 */
public class CodeCacheSegmentsBuilder implements RecordBuilder<GenericRecord, List<CodeCacheSegment>> {

    private static final String CODE_BLOB_TYPE_FIELD = "codeBlobType";
    private static final String START_ADDRESS_FIELD = "startAddress";
    private static final String RESERVED_TOP_ADDRESS_FIELD = "reservedTopAddress";
    private static final String UNALLOCATED_CAPACITY_FIELD = "unallocatedCapacity";
    private static final String ENTRY_COUNT_FIELD = "entryCount";
    private static final String METHOD_COUNT_FIELD = "methodCount";
    private static final String ADAPTOR_COUNT_FIELD = "adaptorCount";
    private static final String FULL_COUNT_FIELD = "fullCount";

    private final Map<String, CodeCacheSegment> segmentsByType = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String codeBlobType = Json.readString(fields, CODE_BLOB_TYPE_FIELD);
        if (codeBlobType == null) {
            return;
        }

        long startAddress = Json.readLong(fields, START_ADDRESS_FIELD);
        long reservedTop = Json.readLong(fields, RESERVED_TOP_ADDRESS_FIELD);
        long reserved = (startAddress >= 0 && reservedTop > startAddress) ? reservedTop - startAddress : 0;
        long unallocated = Math.max(0, Json.readLong(fields, UNALLOCATED_CAPACITY_FIELD));

        segmentsByType.put(codeBlobType, new CodeCacheSegment(
                codeBlobType,
                reserved,
                Math.max(0, reserved - unallocated),
                unallocated,
                Math.max(0, Json.readLong(fields, ENTRY_COUNT_FIELD)),
                Math.max(0, Json.readLong(fields, METHOD_COUNT_FIELD)),
                Math.max(0, Json.readLong(fields, ADAPTOR_COUNT_FIELD)),
                Math.max(0, Json.readLong(fields, FULL_COUNT_FIELD))));
    }

    @Override
    public List<CodeCacheSegment> build() {
        List<CodeCacheSegment> result = new ArrayList<>(segmentsByType.values());
        result.sort(Comparator.comparingLong(CodeCacheSegment::usedBytes).reversed());
        return result;
    }
}
