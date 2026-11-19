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

package cafe.jeffrey.microscope.core.profile.manager.model.nmt;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.core.profile.timeseries.SingleSerie;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesData;

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
    private static final int MAX_CATEGORIES = 8;

    private final RelativeTimeRange timeRange;
    private final Map<String, MaxPerSecondSeries> committedByCategory = new LinkedHashMap<>();

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
        committedByCategory.computeIfAbsent(category, name -> new MaxPerSecondSeries(name, timeRange))
                .record(seconds, committed);
    }

    @Override
    public TimeseriesData build() {
        List<MaxPerSecondSeries> ranked = new ArrayList<>(committedByCategory.values());
        ranked.sort(Comparator.comparingLong(MaxPerSecondSeries::peak).reversed());

        List<SingleSerie> series = new ArrayList<>();
        for (MaxPerSecondSeries category : ranked.subList(0, Math.min(MAX_CATEGORIES, ranked.size()))) {
            series.add(category.buildCarryingForward());
        }
        if (ranked.size() > MAX_CATEGORIES) {
            List<MaxPerSecondSeries> rest = ranked.subList(MAX_CATEGORIES, ranked.size());
            series.add(MaxPerSecondSeries.sumOf(OTHER_SERIES_NAME, rest).buildCarryingForward());
        }
        return new TimeseriesData(series);
    }
}
