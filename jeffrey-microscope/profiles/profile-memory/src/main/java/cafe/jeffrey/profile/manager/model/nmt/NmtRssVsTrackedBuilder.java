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
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

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
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap rssTimeseries;
    private final LongLongHashMap committedTimeseries;

    public NmtRssVsTrackedBuilder(RelativeTimeRange timeRange) {
        this.rssTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.committedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        if (Type.RESIDENT_SET_SIZE.equals(record.type())) {
            long size = Json.readLong(record.jsonFields(), SIZE_FIELD);
            if (size >= 0) {
                rssTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, size));
            }
        } else {
            long committed = Json.readLong(record.jsonFields(), COMMITTED_FIELD);
            if (committed >= 0) {
                committedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, committed));
            }
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie rssSerie = TimeseriesUtils.buildSerie(RSS_SERIES_NAME, rssTimeseries);
        SingleSerie committedSerie = TimeseriesUtils.buildSerie(COMMITTED_SERIES_NAME, committedTimeseries);
        TimeseriesUtils.remapTimeseriesBySteps(rssSerie, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(committedSerie, CARRY_FORWARD_MARK);
        return new TimeseriesData(rssSerie, committedSerie);
    }
}
