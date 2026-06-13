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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData.TenuringAgeBucket;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData.TenuringGcSummary;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups {@code jdk.TenuringDistribution} events (one per gcId+age) into per-collection
 * survivor-age summaries, most recent collection first. The number of returned collections is
 * capped — tenuring analysis cares about recent behavior, not the full history.
 */
public class TenuringDistributionBuilder implements RecordBuilder<GenericRecord, List<TenuringGcSummary>> {

    private static final String GC_ID_FIELD = "gcId";
    private static final String AGE_FIELD = "age";
    private static final String SIZE_FIELD = "size";

    private final int maxCollections;
    private final Map<Long, TreeMap<Integer, Long>> sizesByGcIdAndAge = new HashMap<>();

    public TenuringDistributionBuilder(int maxCollections) {
        if (maxCollections <= 0) {
            throw new IllegalArgumentException("maxCollections must be positive: " + maxCollections);
        }
        this.maxCollections = maxCollections;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        int age = Json.readInt(fields, AGE_FIELD);
        if (gcId < 0 || age < 0) {
            return;
        }
        long size = Math.max(0, Json.readLong(fields, SIZE_FIELD));
        sizesByGcIdAndAge.computeIfAbsent(gcId, key -> new TreeMap<>()).merge(age, size, Long::sum);
    }

    @Override
    public List<TenuringGcSummary> build() {
        List<TenuringGcSummary> result = new ArrayList<>(sizesByGcIdAndAge.size());
        sizesByGcIdAndAge.forEach((gcId, sizesByAge) -> {
            List<TenuringAgeBucket> buckets = sizesByAge.entrySet().stream()
                    .map(entry -> new TenuringAgeBucket(entry.getKey(), entry.getValue()))
                    .toList();
            long totalSize = buckets.stream().mapToLong(TenuringAgeBucket::sizeBytes).sum();
            result.add(new TenuringGcSummary(gcId, totalSize, buckets));
        });
        result.sort(Comparator.comparingLong(TenuringGcSummary::gcId).reversed());
        return result.size() > maxCollections ? List.copyOf(result.subList(0, maxCollections)) : result;
    }
}
