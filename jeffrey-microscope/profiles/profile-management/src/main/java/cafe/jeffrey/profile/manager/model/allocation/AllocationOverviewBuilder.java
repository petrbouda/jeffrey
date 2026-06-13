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

package cafe.jeffrey.profile.manager.model.allocation;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

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
