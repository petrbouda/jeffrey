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
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;

import java.time.temporal.ChronoUnit;

public abstract class TimeseriesBuilder implements RecordBuilder<TimeseriesRecord, TimeseriesData> {

    protected static LongLongHashMap structure(RelativeTimeRange timeRange) {
        long start = timeRange.start().truncatedTo(ChronoUnit.SECONDS)
                .toSeconds();
        long end = timeRange.end().truncatedTo(ChronoUnit.SECONDS)
                .toSeconds();

        LongLongHashMap values = new LongLongHashMap();
        for (long i = start; i <= end; ++i) {
            values.put(i, 0);
        }
        return values;
    }
}
