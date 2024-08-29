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
import pbouda.jeffrey.frameir.record.AllocationRecord;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;

import java.time.Instant;
import java.util.List;

public class AllocationEventProcessor extends StacktraceBasedEventProcessor<AllocationRecord> {

    private final String allocationField;

    public AllocationEventProcessor(List<Type> eventType, AbsoluteTimeRange absoluteTimeRange, boolean threadMode) {
        this(eventType, absoluteTimeRange, new AllocationTreeBuilder(threadMode));
    }

    public AllocationEventProcessor(
            List<Type> eventType,
            AbsoluteTimeRange absoluteTimeRange,
            AllocationTreeBuilder treeBuilder) {

        super(eventType, absoluteTimeRange, treeBuilder);
        this.allocationField = eventType.getFirst().weightFieldName();
    }

    @Override
    protected AllocationRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        return new AllocationRecord(
                modifiedEventTime,
                event.getStackTrace(),
                event.getThread(),
                event.getClass("objectClass"),
                event.getEventType(),
                event.getLong(allocationField));
    }
}
