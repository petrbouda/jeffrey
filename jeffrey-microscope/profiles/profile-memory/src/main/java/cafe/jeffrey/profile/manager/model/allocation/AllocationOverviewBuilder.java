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

package cafe.jeffrey.profile.manager.model.allocation;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * Accumulates allocation totals: overall bytes, the in-TLAB / outside-TLAB split (when TLAB events
 * are used), distinct classes, and the dominant class by bytes. Allocation bytes come from the
 * event weight (TLAB allocation size, or the sampled estimate).
 */
public class AllocationOverviewBuilder implements RecordBuilder<GenericRecord, AllocationOverview> {

    private static final String OBJECT_CLASS_FIELD = "objectClass";

    private final boolean sampled;
    private final Map<String, Long> bytesByClass = new HashMap<>();

    private long totalBytes;
    private long inTlabBytes;
    private long outsideTlabBytes;

    public AllocationOverviewBuilder(boolean sampled) {
        this.sampled = sampled;
    }

    @Override
    public void onRecord(GenericRecord record) {
        long bytes = Math.max(0, record.sampleWeight());
        totalBytes += bytes;

        if (Type.OBJECT_ALLOCATION_IN_NEW_TLAB.equals(record.type())) {
            inTlabBytes += bytes;
        } else if (Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.equals(record.type())) {
            outsideTlabBytes += bytes;
        }

        String objectClass = Json.readString(record.jsonFields(), OBJECT_CLASS_FIELD);
        if (objectClass != null) {
            bytesByClass.merge(objectClass, bytes, Long::sum);
        }
    }

    @Override
    public AllocationOverview build() {
        String dominantType = bytesByClass.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return new AllocationOverview(
                totalBytes,
                inTlabBytes,
                outsideTlabBytes,
                bytesByClass.size(),
                dominantType,
                sampled);
    }
}
