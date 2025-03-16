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

package pbouda.jeffrey.timeseries;

import org.eclipse.collections.impl.map.mutable.primitive.LongBooleanHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;

public abstract class SplitTimeseriesBuilder extends TimeseriesBuilder {

    private final LongLongHashMap values;
    private final LongLongHashMap matchedValues;
    private final LongBooleanHashMap processed = new LongBooleanHashMap();

    public SplitTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.values = structure(timeRange);
        this.matchedValues = structure(timeRange);
    }

    @Override
    public void onRecord(TimeseriesRecord record) {
        if (processStacktrace(record.stacktrace(), record.thread())) {
            matchedValues.addToValue(record.second(), record.value());
        } else {
            values.addToValue(record.second(), record.value());
        }
    }

    private boolean processStacktrace(JfrStackTrace stacktrace, JfrThread thread) {
        if (stacktrace != null) {
            return processed.getIfAbsentPutWithKey(stacktrace.id(), __ -> matchesStacktrace(stacktrace, thread));
        }
        return false;
    }

    protected abstract boolean matchesStacktrace(JfrStackTrace stacktrace, JfrThread thread);

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(
                TimeseriesUtils.buildSerie("Samples", values),
                TimeseriesUtils.buildSerie("Matched Samples", matchedValues));
    }
}
