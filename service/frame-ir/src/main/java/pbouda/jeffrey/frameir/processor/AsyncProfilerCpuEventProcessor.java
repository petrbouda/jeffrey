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
import pbouda.jeffrey.frameir.record.ExecutionSampleRecord;
import pbouda.jeffrey.frameir.record.StackBasedRecord;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.type.JdkStackTrace;
import pbouda.jeffrey.jfrparser.jdk.type.JdkThread;

import java.util.List;

public class AsyncProfilerCpuEventProcessor extends StacktraceBasedEventProcessor<StackBasedRecord> {

    public AsyncProfilerCpuEventProcessor(
            Type eventTypes,
            AbsoluteTimeRange absoluteTimeRange,
            SimpleTreeBuilder treeBuilder) {

        super(List.of(eventTypes), absoluteTimeRange, treeBuilder);
    }

    /**
     * Only STATE_DEFAULT state is valid for CPU samples
     *
     * @param event original recorded event
     * @return true if the event is valid and contains the right state
     */
    @Override
    protected boolean filterEvent(RecordedEvent event) {
        String state = event.getString("state");
        return "STATE_DEFAULT".equals(state);
    }

    @Override
    protected ExecutionSampleRecord mapEvent(RecordedEvent event) {
        return new ExecutionSampleRecord(new JdkStackTrace(event.getStackTrace()), new JdkThread(event));
    }
}
