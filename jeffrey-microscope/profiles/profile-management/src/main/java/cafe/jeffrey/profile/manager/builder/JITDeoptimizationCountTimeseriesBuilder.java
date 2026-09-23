/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.builder;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
