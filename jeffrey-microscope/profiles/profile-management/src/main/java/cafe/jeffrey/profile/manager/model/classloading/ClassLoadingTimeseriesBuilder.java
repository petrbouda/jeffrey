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

package cafe.jeffrey.profile.manager.model.classloading;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds a class-loading timeline from periodic {@code jdk.ClassLoadingStatistics} events. The event
 * carries cumulative gauges ({@code loadedClassCount}, {@code unloadedClassCount}), so each second is
 * filled with the latest sampled value and gaps carry the previous value forward (the gauges are
 * monotonic, never a true zero once loading has started).
 */
public class ClassLoadingTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String LOADED_SERIES_NAME = "Loaded Classes";
    private static final String UNLOADED_SERIES_NAME = "Unloaded Classes";
    private static final String LOADED_CLASS_COUNT_FIELD = "loadedClassCount";
    private static final String UNLOADED_CLASS_COUNT_FIELD = "unloadedClassCount";
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap loadedTimeseries;
    private final LongLongHashMap unloadedTimeseries;

    public ClassLoadingTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.loadedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.unloadedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long loaded = Json.readLong(record.jsonFields(), LOADED_CLASS_COUNT_FIELD);
        if (loaded < 0) {
            return;
        }
        long unloaded = Math.max(0, Json.readLong(record.jsonFields(), UNLOADED_CLASS_COUNT_FIELD));
        long currentlyLoaded = Math.max(0, loaded - unloaded);

        long seconds = record.timestampFromStart().toSeconds();
        loadedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, currentlyLoaded));
        unloadedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, unloaded));
    }

    @Override
    public TimeseriesData build() {
        SingleSerie loadedSerie = TimeseriesUtils.buildSerie(LOADED_SERIES_NAME, loadedTimeseries);
        SingleSerie unloadedSerie = TimeseriesUtils.buildSerie(UNLOADED_SERIES_NAME, unloadedTimeseries);
        // Carry the last sampled gauge value across seconds that had no statistics event.
        TimeseriesUtils.remapTimeseriesBySteps(loadedSerie, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(unloadedSerie, CARRY_FORWARD_MARK);
        return new TimeseriesData(loadedSerie, unloadedSerie);
    }
}
