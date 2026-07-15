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

package cafe.jeffrey.profile.manager.model.nmt;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
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
 * Builds a per-category committed-memory timeline from {@code jdk.NativeMemoryUsage}: one series per
 * category (committed bytes, max per second, carried forward across gaps). To keep the stacked-area
 * chart readable, only the top {@value #MAX_CATEGORIES} categories by peak committed are kept as
 * their own series; the remainder are summed into an "Other" series.
 */
public class NmtCategoryTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String TYPE_FIELD = "type";
    private static final String COMMITTED_FIELD = "committed";
    private static final String UNKNOWN_CATEGORY = "Unknown";
    private static final String OTHER_SERIES_NAME = "Other";
    private static final long CARRY_FORWARD_MARK = 0L;
    private static final int MAX_CATEGORIES = 8;

    private final RelativeTimeRange timeRange;
    private final Map<String, LongLongHashMap> committedByCategory = new LinkedHashMap<>();

    public NmtCategoryTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String category = Json.readString(fields, TYPE_FIELD);
        if (category == null) {
            category = UNKNOWN_CATEGORY;
        }
        long committed = Json.readLong(fields, COMMITTED_FIELD);
        if (committed < 0) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        LongLongHashMap series = committedByCategory.computeIfAbsent(category, key -> TimeseriesUtils.initWithZeros(timeRange));
        series.updateValue(seconds, 0, existing -> Math.max(existing, committed));
    }

    @Override
    public TimeseriesData build() {
        List<String> ranked = new ArrayList<>(committedByCategory.keySet());
        ranked.sort(Comparator.comparingLong((String category) -> committedByCategory.get(category).max()).reversed());

        List<SingleSerie> series = new ArrayList<>();
        LongLongHashMap other = null;
        for (int i = 0; i < ranked.size(); i++) {
            String category = ranked.get(i);
            LongLongHashMap values = committedByCategory.get(category);
            if (i < MAX_CATEGORIES) {
                series.add(buildSerie(category, values));
            } else {
                if (other == null) {
                    other = TimeseriesUtils.initWithZeros(timeRange);
                }
                LongLongHashMap target = other;
                values.forEachKeyValue(target::addToValue);
            }
        }
        if (other != null) {
            series.add(buildSerie(OTHER_SERIES_NAME, other));
        }
        return new TimeseriesData(series);
    }

    private static SingleSerie buildSerie(String name, LongLongHashMap values) {
        SingleSerie serie = TimeseriesUtils.buildSerie(name, values);
        TimeseriesUtils.remapTimeseriesBySteps(serie, CARRY_FORWARD_MARK);
        return serie;
    }
}
