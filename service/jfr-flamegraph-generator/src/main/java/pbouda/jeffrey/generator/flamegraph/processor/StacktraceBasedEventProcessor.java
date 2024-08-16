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

package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;
import pbouda.jeffrey.generator.flamegraph.tree.FrameTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.time.Instant;
import java.util.List;

public abstract class StacktraceBasedEventProcessor<T extends StackBasedRecord> implements EventProcessor<Frame> {

    private final AbsoluteTimeRange timeRange;
    private final ProcessableEvents processableEvents;
    private final FrameTreeBuilder<T> treeBuilder;

    public StacktraceBasedEventProcessor(
            List<Type> eventTypes,
            AbsoluteTimeRange timeRange,
            FrameTreeBuilder<T> treeBuilder) {

        this.timeRange = timeRange;
        this.processableEvents = new ProcessableEvents(eventTypes);
        this.treeBuilder = treeBuilder;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return processableEvents;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

        if (eventTime.isBefore(timeRange.start()) || eventTime.isAfter(timeRange.end())) {
            return Result.CONTINUE;
        }

        treeBuilder.addRecord(mapEvent(event, eventTime));
        return Result.CONTINUE;
    }

    /**
     * Maps the {@link RecordedEvent} into the object for with all needed fields
     * from the event. It also provides {@code modifiedEventTime} because
     * the event's eventTime can be modified by the parent to e.g. correlate
     * two flamegraphs together.
     *
     * @param event             original recorded event
     * @param modifiedEventTime eventTime from the event that can be modified by the parent
     * @return mapped object with important fields from the event
     */
    abstract protected T mapEvent(RecordedEvent event, Instant modifiedEventTime);

    @Override
    public Frame get() {
        return treeBuilder.build();
    }
}
