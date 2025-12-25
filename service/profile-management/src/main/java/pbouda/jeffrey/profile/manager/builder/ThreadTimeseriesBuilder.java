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

package pbouda.jeffrey.profile.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;

public class ThreadTimeseriesBuilder implements RecordBuilder<GenericRecord, SingleSerie> {

    private final LongLongHashMap values;

    public ThreadTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.initWithZeros(timeRange, 0);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonNodes = record.jsonFields();
        long currActive = jsonNodes.get("activeCount").asLong();

        Duration timestamp = record.timestampFromStart();
        values.updateValue(timestamp.toSeconds(), 0, v -> Math.max(currActive, v));
    }

    @Override
    public SingleSerie build() {
        SingleSerie serie = TimeseriesUtils.buildSerie("Active Threads", values);
        // Complete the gabs in the timeseries and fill them with previous values (step-wise)
        TimeseriesUtils.remapTimeseriesBySteps(serie, 0);
        return serie;
    }
}
