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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData.GcReferenceBreakdown;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData.Header;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData.ReferenceTypeStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.GCReferenceStatistics} into per-type totals, a per-second timeline (one series
 * per reference type) and a per-collection breakdown. The JDK 26 event carries only {@code gcId},
 * {@code type} and {@code count}, so all outputs are count-based.
 */
public class ReferenceProcessingBuilder implements RecordBuilder<GenericRecord, ReferenceProcessingData> {

    private static final String TYPE_FIELD = "type";
    private static final String COUNT_FIELD = "count";
    private static final String GC_ID_FIELD = "gcId";

    private final RelativeTimeRange timeRange;
    private final int maxGcs;

    private final Map<String, Long> totalsByType = new LinkedHashMap<>();
    private final Map<String, LongLongHashMap> timelineByType = new LinkedHashMap<>();
    private final Map<Long, Map<String, Long>> perGc = new LinkedHashMap<>();

    public ReferenceProcessingBuilder(RelativeTimeRange timeRange, int maxGcs) {
        if (maxGcs <= 0) {
            throw new IllegalArgumentException("maxGcs must be positive: " + maxGcs);
        }
        this.timeRange = timeRange;
        this.maxGcs = maxGcs;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String type = Json.readString(fields, TYPE_FIELD);
        if (type == null) {
            return;
        }
        long count = Math.max(0, Json.readLong(fields, COUNT_FIELD));
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        long seconds = record.timestampFromStart().toSeconds();

        totalsByType.merge(type, count, Long::sum);
        timelineByType.computeIfAbsent(type, key -> TimeseriesUtils.initWithZeros(timeRange))
                .addToValue(seconds, count);
        perGc.computeIfAbsent(gcId, key -> new LinkedHashMap<>())
                .merge(type, count, Long::sum);
    }

    @Override
    public ReferenceProcessingData build() {
        long gcCount = perGc.size();

        List<ReferenceTypeStat> byType = totalsByType.entrySet().stream()
                .map(entry -> new ReferenceTypeStat(
                        entry.getKey(), entry.getValue(), gcCount == 0 ? 0 : entry.getValue() / gcCount))
                .sorted(Comparator.comparingLong(ReferenceTypeStat::total).reversed())
                .toList();

        List<SingleSerie> series = new ArrayList<>(byType.size());
        for (ReferenceTypeStat stat : byType) {
            series.add(TimeseriesUtils.buildSerie(stat.type(), timelineByType.get(stat.type())));
        }

        List<GcReferenceBreakdown> perGcBreakdown = perGc.entrySet().stream()
                .map(entry -> new GcReferenceBreakdown(
                        entry.getKey(), entry.getValue().values().stream().mapToLong(Long::longValue).sum(),
                        entry.getValue()))
                .sorted(Comparator.comparingLong(GcReferenceBreakdown::total).reversed())
                .limit(maxGcs)
                .toList();

        long totalReferences = totalsByType.values().stream().mapToLong(Long::longValue).sum();
        String dominantType = byType.isEmpty() ? null : byType.getFirst().type();
        Header header = new Header(totalReferences, byType.size(), gcCount, dominantType);

        return new ReferenceProcessingData(header, byType, new TimeseriesData(series), perGcBreakdown);
    }
}
