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

package cafe.jeffrey.profile.manager.model.classloading;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
