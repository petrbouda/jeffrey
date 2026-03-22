/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager.model.gc;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.provider.profile.builder.RecordBuilder;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds timeseries data with separate series for Young, Old, and Full generation GC events.
 * Uses the "name" field from jdk.GarbageCollection events to classify events by generation.
 */
public class GCGenerationTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String YOUNG_GC_SERIES_NAME = "Young GC";
    private static final String OLD_GC_SERIES_NAME = "Old GC";
    private static final String FULL_GC_SERIES_NAME = "Full GC";

    private final GCTimeseriesType timeseriesType;
    private final GarbageCollectorType gcType;
    private final LongLongHashMap youngTimeseries;
    private final LongLongHashMap oldTimeseries;
    private final LongLongHashMap fullGCTimeseries;

    public GCGenerationTimeseriesBuilder(
            RelativeTimeRange timeRange,
            GCTimeseriesType timeseriesType,
            GarbageCollectorType gcType) {
        this.timeseriesType = timeseriesType;
        this.gcType = gcType;
        this.youngTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.oldTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.fullGCTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        String collectorName = Json.readString(record.jsonFields(), "name");

        LongLongHashMap targetTimeseries = classifyByGeneration(collectorName);
        if (targetTimeseries == null) {
            return;
        }

        switch (timeseriesType) {
            case COUNT -> targetTimeseries.addToValue(seconds, 1);
            case MAX_PAUSE -> {
                long pause = Json.readLong(record.jsonFields(), "longestPause");
                targetTimeseries.updateValue(seconds, -1, first -> Math.max(first, pause));
            }
            case SUM_OF_PAUSES -> {
                long pause = Json.readLong(record.jsonFields(), "sumOfPauses");
                targetTimeseries.addToValue(seconds, pause);
            }
        }
    }

    /**
     * Classifies the collector name as young, old, or full generation.
     * Full GC events (e.g., "G1Full") are checked first before Young/Old classification.
     *
     * @param collectorName the name of the garbage collector from the event
     * @return the appropriate timeseries map, or null if the collector is unknown
     */
    private LongLongHashMap classifyByGeneration(String collectorName) {
        if (collectorName == null) {
            return null;
        }

        // Check for Full GC first (e.g., "G1Full", "Full", etc.)
        if (collectorName.toLowerCase().contains("full")) {
            return fullGCTimeseries;
        }

        String youngCollector = gcType.getYoungGenCollector();
        String oldCollector = gcType.getOldGenCollector();

        if (youngCollector != null && collectorName.equalsIgnoreCase(youngCollector)) {
            return youngTimeseries;
        } else if (oldCollector != null && collectorName.equalsIgnoreCase(oldCollector)) {
            return oldTimeseries;
        }

        return null;
    }

    @Override
    public TimeseriesData build() {
        SingleSerie youngSerie = TimeseriesUtils.buildSerie(YOUNG_GC_SERIES_NAME, youngTimeseries);
        SingleSerie oldSerie = TimeseriesUtils.buildSerie(OLD_GC_SERIES_NAME, oldTimeseries);
        SingleSerie fullSerie = TimeseriesUtils.buildSerie(FULL_GC_SERIES_NAME, fullGCTimeseries);
        return new TimeseriesData(youngSerie, oldSerie, fullSerie);
    }
}
