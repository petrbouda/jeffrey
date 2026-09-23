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
import cafe.jeffrey.provider.profile.api.TimeseriesSearchRecord;

public class TimeseriesSearchBuilder implements RecordBuilder<TimeseriesSearchRecord, TimeseriesData> {

    private final LongLongHashMap values;
    private final LongLongHashMap matchedValues;

    public TimeseriesSearchBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.initWithZeros(timeRange);
        this.matchedValues = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(TimeseriesSearchRecord record) {
        matchedValues.addToValue(record.second(), record.matched());
        values.addToValue(record.second(), record.total() - record.matched());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(
                TimeseriesUtils.buildSerie("Samples", values),
                TimeseriesUtils.buildSerie("Matched Samples", matchedValues));
    }
}
