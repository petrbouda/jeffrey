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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;

import java.time.temporal.ChronoUnit;

public abstract class TimeseriesBuilder<T extends StackBasedRecord, R> implements RecordBuilder<T, R> {

    @Override
    public void onRecord(T record) {
        long second = record.timestampFromStart().truncatedTo(ChronoUnit.SECONDS)
                .toMillis();

        incrementCounter(record, second);
    }

    protected static LongLongHashMap structure(RelativeTimeRange timeRange) {
        LongLongHashMap values = new LongLongHashMap();
        long start = timeRange.start().truncatedTo(ChronoUnit.SECONDS)
                .toMillis();
        long end = timeRange.end().truncatedTo(ChronoUnit.SECONDS)
                .toMillis();

        for (long i = start; i <= end; i += 1000) {
            values.put(i, 0);
        }
        return values;
    }

    protected abstract void incrementCounter(T event, long second);
}
