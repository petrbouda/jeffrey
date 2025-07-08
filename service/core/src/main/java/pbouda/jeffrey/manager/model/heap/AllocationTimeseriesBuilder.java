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

package pbouda.jeffrey.manager.model.heap;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.time.temporal.ChronoUnit;

public class AllocationTimeseriesBuilder implements RecordBuilder<GenericRecord, SingleSerie> {

    private final HeapMemoryTimeseriesType timeseriesType;
    private final LongLongHashMap timeseries;

    public AllocationTimeseriesBuilder(RelativeTimeRange timeRange, HeapMemoryTimeseriesType timeseriesType) {
        this.timeseriesType = timeseriesType;
        this.timeseries = TimeseriesUtils.initWithZeros(timeRange, ChronoUnit.SECONDS);
    }

    @Override
    public void onRecord(GenericRecord record) {
        processAllocationEvent(record);
    }

    private void processAllocationEvent(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long tlabSize = Json.readLong(fields, "tlabSize");
        long seconds = record.timestampFromStart().toSeconds();

        timeseries.addToValue(seconds, tlabSize);
    }

    @Override
    public SingleSerie build() {
        return TimeseriesUtils.buildSerie(timeseriesType.getDescription(), timeseries);
    }
}
