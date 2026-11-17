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

package cafe.jeffrey.profile.manager.thread.builder;

import tools.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesUtils;

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
