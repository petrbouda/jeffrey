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
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData.ReferenceStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sums {@code jdk.GCReferenceStatistics} counts per reference type across the recording,
 * ordered by descending total.
 */
public class ReferenceStatsBuilder implements RecordBuilder<GenericRecord, List<ReferenceStat>> {

    private static final String TYPE_FIELD = "type";
    private static final String COUNT_FIELD = "count";

    private final Map<String, Long> countsByType = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String type = Json.readString(fields, TYPE_FIELD);
        if (type == null) {
            return;
        }
        long count = Math.max(0, Json.readLong(fields, COUNT_FIELD));
        countsByType.merge(type, count, Long::sum);
    }

    @Override
    public List<ReferenceStat> build() {
        List<ReferenceStat> result = new ArrayList<>(countsByType.size());
        countsByType.forEach((type, count) -> result.add(new ReferenceStat(type, count)));
        result.sort(Comparator.comparingLong(ReferenceStat::totalCount).reversed());
        return result;
    }
}
