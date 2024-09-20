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
import pbouda.jeffrey.jfrparser.jdk.type.JdkClass;
import pbouda.jeffrey.jfrparser.jdk.type.JdkStackTrace;
import pbouda.jeffrey.jfrparser.jdk.type.JdkThread;

import java.time.Instant;
import java.util.List;

public class AllocationEventProcessor extends StacktraceBasedEventProcessor<AllocationRecord> {

    private final String allocationField;
    private final boolean threadMode;

    public AllocationEventProcessor(
            List<Type> eventType,
            AbsoluteTimeRange absoluteTimeRange,
            boolean threadMode) {

        this(eventType, absoluteTimeRange, new AllocationTreeBuilder(threadMode), threadMode);
    }

    public AllocationEventProcessor(
            List<Type> eventType,
            AbsoluteTimeRange absoluteTimeRange,
            AllocationTreeBuilder treeBuilder,
            boolean threadMode) {

        super(eventType, absoluteTimeRange, treeBuilder);
        this.allocationField = eventType.getFirst().weightFieldName();
        this.threadMode = threadMode;
    }

    @Override
    protected AllocationRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        JdkStackTrace stackTrace = new JdkStackTrace(event.getStackTrace());
        JdkClass clazz = new JdkClass(event.getClass("objectClass"));

        if (!threadMode) {
            return new AllocationRecord(
                    modifiedEventTime,
                    stackTrace,
                    clazz,
                    event.getEventType(),
                    event.getLong(allocationField));
        }
        return new AllocationRecord(
                modifiedEventTime,
                stackTrace,
                new JdkThread(event.getThread()),
                new JdkClass(event.getClass("objectClass")),
                event.getEventType(),
                event.getLong(allocationField));
    }
}
