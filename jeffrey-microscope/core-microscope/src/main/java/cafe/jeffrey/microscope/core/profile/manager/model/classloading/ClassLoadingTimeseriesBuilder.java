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

package cafe.jeffrey.microscope.core.profile.manager.model.classloading;

import cafe.jeffrey.microscope.core.profile.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesData;

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

    private final MaxPerSecondSeries loadedTimeseries;
    private final MaxPerSecondSeries unloadedTimeseries;

    public ClassLoadingTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.loadedTimeseries = new MaxPerSecondSeries(LOADED_SERIES_NAME, timeRange);
        this.unloadedTimeseries = new MaxPerSecondSeries(UNLOADED_SERIES_NAME, timeRange);
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
        loadedTimeseries.record(seconds, currentlyLoaded);
        unloadedTimeseries.record(seconds, unloaded);
    }

    @Override
    public TimeseriesData build() {
        // Carry the last sampled gauge value across seconds that had no statistics event.
        return new TimeseriesData(loadedTimeseries.buildCarryingForward(), unloadedTimeseries.buildCarryingForward());
    }
}
