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
import pbouda.jeffrey.provider.api.streamer.model.SecondValue;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;

public class SimpleTimeseriesBuilder implements RecordBuilder<TimeseriesRecord, TimeseriesData> {

    private final LongLongHashMap values;

    public SimpleTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.structure(timeRange);
    }

    @Override
    public void onRecord(TimeseriesRecord record) {
        SecondValue first = record.values().getFirst();
        values.addToValue(first.second(), first.value());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(TimeseriesUtils.buildSerie("Samples", values));
    }
}
