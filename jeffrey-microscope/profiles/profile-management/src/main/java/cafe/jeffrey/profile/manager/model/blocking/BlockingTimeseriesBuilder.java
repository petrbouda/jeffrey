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

package cafe.jeffrey.profile.manager.model.blocking;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.List;

/**
 * Counts blocking-event occurrences per second, one series per blocking type, so the Blocking
 * Operations page can show all flavours on a single timeline. Series are always emitted in a fixed
 * order with stable names (an absent type is a flat-zero line, keeping the legend consistent).
 */
public class BlockingTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String MONITOR_SERIES_NAME = "Lock Contention";
    private static final String WAIT_SERIES_NAME = "Monitor Waits";
    private static final String PARK_SERIES_NAME = "Thread Parks";
    private static final String SLEEP_SERIES_NAME = "Thread Sleeps";
    private static final String PINNED_SERIES_NAME = "Pinning";

    private final LongLongHashMap monitors;
    private final LongLongHashMap waits;
    private final LongLongHashMap parks;
    private final LongLongHashMap sleeps;
    private final LongLongHashMap pinned;

    public BlockingTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.monitors = TimeseriesUtils.initWithZeros(timeRange);
        this.waits = TimeseriesUtils.initWithZeros(timeRange);
        this.parks = TimeseriesUtils.initWithZeros(timeRange);
        this.sleeps = TimeseriesUtils.initWithZeros(timeRange);
        this.pinned = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        LongLongHashMap series = seriesFor(record.type());
        if (series == null) {
            return;
        }
        series.addToValue(record.timestampFromStart().toSeconds(), 1);
    }

    private LongLongHashMap seriesFor(Type type) {
        if (Type.JAVA_MONITOR_ENTER.equals(type)) {
            return monitors;
        }
        if (Type.JAVA_MONITOR_WAIT.equals(type)) {
            return waits;
        }
        if (Type.THREAD_PARK.equals(type)) {
            return parks;
        }
        if (Type.THREAD_SLEEP.equals(type)) {
            return sleeps;
        }
        if (Type.VIRTUAL_THREAD_PINNED.equals(type)) {
            return pinned;
        }
        return null;
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(List.of(
                TimeseriesUtils.buildSerie(MONITOR_SERIES_NAME, monitors),
                TimeseriesUtils.buildSerie(WAIT_SERIES_NAME, waits),
                TimeseriesUtils.buildSerie(PARK_SERIES_NAME, parks),
                TimeseriesUtils.buildSerie(SLEEP_SERIES_NAME, sleeps),
                TimeseriesUtils.buildSerie(PINNED_SERIES_NAME, pinned)));
    }
}
