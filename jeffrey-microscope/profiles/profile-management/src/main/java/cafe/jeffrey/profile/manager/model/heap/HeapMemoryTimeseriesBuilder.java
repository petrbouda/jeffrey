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

package cafe.jeffrey.profile.manager.model.heap;

import tools.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
