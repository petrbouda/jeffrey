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
import pbouda.jeffrey.frameir.processor.FilterableEventProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;

public abstract class TimeseriesEventProcessor<T> extends FilterableEventProcessor<T> {

    public TimeseriesEventProcessor(
            Type eventType,
            AbsoluteTimeRange timeRange,
            Predicate<RecordedEvent> filtering,
            long timeShift) {

        super(List.of(eventType), timeRange, filtering, timeShift);
    }

    @Override
    protected Result processEvent(RecordedEvent event, Instant eventTime) {
        long second = eventTime.truncatedTo(ChronoUnit.SECONDS)
                .toEpochMilli();

        incrementCounter(event, second);
        return Result.CONTINUE;
    }

    protected abstract void incrementCounter(RecordedEvent event, long second);
}
