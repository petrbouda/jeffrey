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
import pbouda.jeffrey.common.time.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.tree.FrameTreeBuilder;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public abstract class StacktraceBasedEventProcessor<T extends StackBasedRecord> extends FilterableEventProcessor<Frame> {

    private final FrameTreeBuilder<T> treeBuilder;

    public StacktraceBasedEventProcessor(
            List<Type> eventTypes,
            AbsoluteTimeRange timeRange,
            Duration timeShift,
            FrameTreeBuilder<T> treeBuilder,
            Predicate<RecordedEvent> filtering) {

        super(eventTypes, timeRange, timeShift, filtering);
        this.treeBuilder = treeBuilder;
    }

    @Override
    protected Result processEvent(RecordedEvent event, Instant eventTime) {
        treeBuilder.onRecord(mapEvent(event));
        return Result.CONTINUE;
    }

    /**
     * Maps the {@link RecordedEvent} into the object for with all needed fields
     * from the event.
     *
     * @param event original recorded event
     * @return mapped object with important fields from the event
     */
    abstract protected T mapEvent(RecordedEvent event);

    @Override
    public Frame get() {
        return treeBuilder.build();
    }
}
