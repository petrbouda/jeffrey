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
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.temporal.ChronoUnit;

public class HeapMemoryTimeseriesBuilder implements RecordBuilder<GenericRecord, SingleSerie> {

    private static final String WHEN_BEFORE_GC = "Before GC";
    private static final String WHEN_AFTER_GC = "After GC";

    private final HeapMemoryTimeseriesType timeseriesType;
    private final LongLongHashMap timeseries;

    // The event timestamp (in nanos) backing the value currently stored for each second bucket.
    // Keeps the series deterministic when events stream out of order: a bucket always holds the
    // value of the event with the greatest timestamp, not of the event that happened to arrive last.
    private final LongLongHashMap latestEventNanosPerSecond = new LongLongHashMap();

    public HeapMemoryTimeseriesBuilder(RelativeTimeRange timeRange, HeapMemoryTimeseriesType timeseriesType) {
        this.timeseriesType = timeseriesType;
        this.timeseries = TimeseriesUtils.init(timeRange, ChronoUnit.SECONDS);
    }

    @Override
    public void onRecord(GenericRecord record) {
        processHeapSummaryEvent(record);
    }

    private void processHeapSummaryEvent(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String when = Json.readString(fields, "when");
        long heapUsed = Json.readLong(fields, "heapUsed");
        long eventNanos = record.timestampFromStart().toNanos();
        long seconds = record.timestampFromStart().toSeconds();

        // Combine both before and after GC events into a single series
        if (WHEN_BEFORE_GC.equals(when) || WHEN_AFTER_GC.equals(when)) {
            long latestNanos = latestEventNanosPerSecond.getIfAbsent(seconds, Long.MIN_VALUE);
            if (eventNanos >= latestNanos) {
                latestEventNanosPerSecond.put(seconds, eventNanos);
                timeseries.put(seconds, heapUsed);
            }
        }
    }

    @Override
    public SingleSerie build() {
        return TimeseriesUtils.buildSerie(timeseriesType.getDescription(), timeseries);
    }
}
