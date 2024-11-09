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

package pbouda.jeffrey.frameir.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public abstract class FilterableEventProcessor<T> implements EventProcessor<T> {

    public static final Predicate<RecordedEvent> NO_FILTER = event -> true;

    private final AbsoluteTimeRange timeRange;
    private final ProcessableEvents processableEvents;
    private final Predicate<RecordedEvent> filtering;
    private final Duration timeShift;
    private final boolean usesTimeShift;

    public FilterableEventProcessor(
            List<Type> eventTypes,
            AbsoluteTimeRange timeRange,
            Duration timeShift,
            Predicate<RecordedEvent> filtering) {

        this.processableEvents = new ProcessableEvents(eventTypes);
        this.filtering = filtering;
        this.timeShift = timeShift;
        this.timeRange = timeRange;
        this.usesTimeShift = timeShift != Duration.ZERO;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return processableEvents;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

        if (usesTimeShift) {
            // timeShift is difference between the start of primary and secondary recording
            // if start of the primary recording is sooner than the secondary one, then the timeShift is positive
            // we need to reduce `event-time` by the timeShift to get the time of the event in the secondary recording
            // if the secondary recording starts sooner, then the timeShift is negative and using `minus`
            // will increase the `event-time`
            eventTime = eventTime.minus(timeShift);
        }
        if (eventTime.isBefore(timeRange.start()) || eventTime.isAfter(timeRange.end())) {
            return Result.CONTINUE;
        }
        if (filterEvent(event)) {
            return processEvent(event, eventTime);
        }
        return Result.CONTINUE;
    }

    /**
     * Possibility to filter the event before mapping and processing it.
     *
     * @param event original recorded event
     * @return true if the event should be processed, false otherwise
     */
    protected boolean filterEvent(RecordedEvent event) {
        return filtering.test(event);
    }

    /**
     * Maps the {@link RecordedEvent} into the object for with all needed fields
     * from the event.
     *
     * @param event     original recorded event
     * @param eventTime time of the event occurrence
     * @return result of the processing, whether to continue or stop the processing
     */
    abstract protected Result processEvent(RecordedEvent event, Instant eventTime);

}
