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

package cafe.jeffrey.microscope.profile.management.manager.model.nmt;

import cafe.jeffrey.microscope.profile.management.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.profile.management.timeseries.SingleSerie;
import cafe.jeffrey.microscope.profile.management.timeseries.TimeseriesData;

/**
 * Builds the RSS-vs-NMT-committed overlay from a mixed stream of {@code jdk.ResidentSetSize}
 * ({@code size}) and {@code jdk.NativeMemoryUsageTotal} ({@code committed}). The gap between RSS
 * (what the OS sees) and total committed (what NMT accounts for) approximates untracked memory
 * (raw {@code malloc}, mappings NMT doesn't see). Both are carried-forward gauges.
 */
public class NmtRssVsTrackedBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String RSS_SERIES_NAME = "Resident Set Size";
    private static final String COMMITTED_SERIES_NAME = "NMT Committed";
    private static final String SIZE_FIELD = "size";
    private static final String COMMITTED_FIELD = "committed";

    private final MaxPerSecondSeries rssTimeseries;
    private final MaxPerSecondSeries committedTimeseries;

    public NmtRssVsTrackedBuilder(RelativeTimeRange timeRange) {
        this.rssTimeseries = new MaxPerSecondSeries(RSS_SERIES_NAME, timeRange);
        this.committedTimeseries = new MaxPerSecondSeries(COMMITTED_SERIES_NAME, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        if (Type.RESIDENT_SET_SIZE.equals(record.type())) {
            long size = Json.readLong(record.jsonFields(), SIZE_FIELD);
            if (size >= 0) {
                rssTimeseries.record(seconds, size);
            }
        } else {
            long committed = Json.readLong(record.jsonFields(), COMMITTED_FIELD);
            if (committed >= 0) {
                committedTimeseries.record(seconds, committed);
            }
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie rssSerie = rssTimeseries.buildCarryingForward();
        SingleSerie committedSerie = committedTimeseries.buildCarryingForward();
        return new TimeseriesData(rssSerie, committedSerie);
    }
}
