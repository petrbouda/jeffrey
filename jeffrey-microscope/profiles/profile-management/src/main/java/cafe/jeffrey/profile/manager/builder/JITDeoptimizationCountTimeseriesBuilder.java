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

package cafe.jeffrey.profile.manager.builder;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Counts jdk.Deoptimization events per second of the recording window.
 * Mirrors {@link cafe.jeffrey.timeseries.SimpleTimeseriesBuilder} but adapts the {@link GenericRecord}
 * input (which has no aggregate count column — we count one per record).
 */
public class JITDeoptimizationCountTimeseriesBuilder implements RecordBuilder<GenericRecord, SingleSerie> {

    private final String serieName;
    private final LongLongHashMap values;

    public JITDeoptimizationCountTimeseriesBuilder(String serieName, RelativeTimeRange timeRange) {
        this.serieName = serieName;
        this.values = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        if (record.timestampFromStart() == null) {
            return;
        }
        long second = record.timestampFromStart().toSeconds();
        values.addToValue(second, 1);
    }

    @Override
    public SingleSerie build() {
        return TimeseriesUtils.buildSerie(serieName, values);
    }
}
