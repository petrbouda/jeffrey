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
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.SecondValue;
import cafe.jeffrey.provider.profile.api.TimeseriesRecord;

public abstract class SplitTimeseriesBuilder implements RecordBuilder<TimeseriesRecord, TimeseriesData> {

    private final LongLongHashMap values;
    private final LongLongHashMap matchedValues;

    private long counter = 0;

    public SplitTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.initWithZeros(timeRange);
        this.matchedValues = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(TimeseriesRecord record) {
        LongLongHashMap collection = matchesStacktrace(record.stacktrace()) ? matchedValues : values;
        for (SecondValue secondValue : record.values()) {
            counter = counter + secondValue.value();
            collection.addToValue(secondValue.second(), secondValue.value());
        }
    }

    protected abstract boolean matchesStacktrace(JfrStackTrace stacktrace);

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(
                TimeseriesUtils.buildSerie("Samples", values),
                TimeseriesUtils.buildSerie("Matched Samples", matchedValues));
    }
}
