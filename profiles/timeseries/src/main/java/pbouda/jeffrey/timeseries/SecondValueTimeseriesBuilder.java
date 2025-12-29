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
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.model.SecondValue;

public class SecondValueTimeseriesBuilder implements RecordBuilder<SecondValue, TimeseriesData> {

    private final String serieName;
    private final LongLongHashMap values;

    public SecondValueTimeseriesBuilder(String serieName, RelativeTimeRange timeRange) {
        this.serieName = serieName;
        this.values = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(SecondValue record) {
        values.addToValue(record.second(), record.value());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(TimeseriesUtils.buildSerie(serieName, values));
    }
}
