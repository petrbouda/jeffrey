/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager.model.heap;

import tools.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.temporal.ChronoUnit;

public class HeapAllocationTimeseriesBuilder implements RecordBuilder<GenericRecord, SingleSerie> {

    private final HeapMemoryTimeseriesType timeseriesType;
    private final LongLongHashMap timeseries;

    public HeapAllocationTimeseriesBuilder(RelativeTimeRange timeRange, HeapMemoryTimeseriesType timeseriesType) {
        this.timeseriesType = timeseriesType;
        this.timeseries = TimeseriesUtils.initWithZeros(timeRange, ChronoUnit.SECONDS);
    }

    @Override
    public void onRecord(GenericRecord record) {
        processAllocationEvent(record);
    }

    private void processAllocationEvent(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long allocated;
        if (record.type() == Type.OBJECT_ALLOCATION_IN_NEW_TLAB) {
            allocated = Json.readLong(fields, "tlabSize");
        } else if (record.type() == Type.OBJECT_ALLOCATION_OUTSIDE_TLAB) {
            allocated = Json.readLong(fields, "allocationSize");
        } else if (record.type() == Type.OBJECT_ALLOCATION_SAMPLE) {
            allocated = Json.readLong(fields, "weight");
        } else {
            throw new IllegalArgumentException("Unsupported allocation event type: " + record.type());
        }
        long seconds = record.timestampFromStart().toSeconds();
        timeseries.addToValue(seconds, allocated);
    }

    @Override
    public SingleSerie build() {
        return TimeseriesUtils.buildSerie(timeseriesType.getDescription(), timeseries);
    }
}
