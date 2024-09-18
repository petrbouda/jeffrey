/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.generator.subsecond;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.SingleEventProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

public class SubSecondEventProcessor extends SingleEventProcessor<SingleResult> {

    private final long startTimeMillis;
    private final Instant endTime;
    private final List<SecondColumn> columns = new ArrayList<>();
    private final boolean collectWeight;

    private long maxvalue = 0;

    public SubSecondEventProcessor(SubSecondConfig config) {
        this(config.eventType(), config.profilingStartTime(), config.generatingStart(),
                config.duration(), config.collectWeight());
    }

    public SubSecondEventProcessor(
            Type eventType,
            Instant profilingStart,
            Duration generatingStart,
            Duration duration,
            boolean collectWeight) {

        super(eventType);
        this.collectWeight = collectWeight;

        Instant startTime = profilingStart.plus(generatingStart);
        this.startTimeMillis = startTime.toEpochMilli();

        if (duration != null && !duration.isZero()) {
            this.endTime = startTime.plus(duration);
        } else {
            this.endTime = null;
        }
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

        // This event is after the end of the processing, skip it.
        // We cannot finish the whole processing, the events are not sorted by time.
        // TODO: More sophisticated parsing using chunks? Skip when the chunk was created after the end-time?
        if (endTime != null && eventTime.isAfter(endTime)) {
            return Result.CONTINUE;
        }

        Instant relative = eventTime.minusMillis(startTimeMillis);
        int relativeSeconds = (int) relative.getEpochSecond();
        int millisInSecond = relative.get(ChronoField.MILLI_OF_SECOND);

        // Value for the new second/column arrived, then create a new column for it.
        int expectedColumns = relativeSeconds + 1;
        if (expectedColumns > columns.size()) {
            appendMoreColumns(expectedColumns);
        }

        long value = 1;
        if (collectWeight) {
            value = eventType()
                    .weightExtractor()
                    .apply(event);
        }

        // Increment a value in the bucket and return a new value to track the
        // `maxvalue` from all buckets and columns.
        long newValue = columns.get(relativeSeconds).increment(millisInSecond, value);
        if (newValue > maxvalue) {
            maxvalue = newValue;
        }

        return Result.CONTINUE;
    }

    private void appendMoreColumns(long newSize) {
        long columnsToAdd = newSize - columns.size();
        for (int i = 0; i < columnsToAdd; i++) {
            columns.add(new SecondColumn());
        }
    }

    @Override
    public SingleResult get() {
        return new SingleResult(maxvalue, columns);
    }
}
