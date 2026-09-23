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

package cafe.jeffrey.timeseries;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.SecondValue;
import cafe.jeffrey.provider.profile.api.TimeseriesRecord;

public class SimpleTimeseriesBuilder implements RecordBuilder<TimeseriesRecord, TimeseriesData> {

    private final String serieName;
    private final LongLongHashMap values;

    public SimpleTimeseriesBuilder(RelativeTimeRange timeRange) {
        this("Samples", timeRange);
    }

    public SimpleTimeseriesBuilder(String serieName, RelativeTimeRange timeRange) {
        this.serieName = serieName;
        this.values = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(TimeseriesRecord record) {
        SecondValue first = record.values().getFirst();
        values.addToValue(first.second(), first.value());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(TimeseriesUtils.buildSerie(serieName, values));
    }
}
