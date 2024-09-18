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

package pbouda.jeffrey.generator.timeseries;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.SingleEventProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

public abstract class TimeseriesEventProcessor<T> extends SingleEventProcessor<T> {

    private final long timeShift;
    private final AbsoluteTimeRange timeRange;

    final Function<RecordedEvent, Long> valueExtractor;

    public TimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange timeRange,
            long timeShift) {

        super(eventType);
        this.valueExtractor = valueExtractor;
        this.timeShift = timeShift;
        this.timeRange = timeRange;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

        // TimeShift to correlate 2 timeseries and different start-times 
        eventTime = eventTime.plusMillis(timeShift);

        if (eventTime.isBefore(timeRange.start()) || eventTime.isAfter(timeRange.end())) {
            return Result.CONTINUE;
        }

        long second = eventTime.truncatedTo(ChronoUnit.SECONDS)
                .toEpochMilli();

        incrementCounter(event, second);

        return Result.CONTINUE;
    }

    protected abstract void incrementCounter(RecordedEvent event, long second);

}
