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
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilter;
import pbouda.jeffrey.frameir.record.SimpleRecord;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.type.JdkStackTrace;
import pbouda.jeffrey.jfrparser.jdk.type.JdkThread;

import java.time.Duration;
import java.util.List;
import java.util.function.ToLongFunction;

public class MallocEventProcessor extends StacktraceBasedEventProcessor<SimpleRecord> {

    private final static List<Type> TYPES = List.of(Type.MALLOC);
    private final ToLongFunction<RecordedEvent> weightExtractor;

    public MallocEventProcessor(
            AbsoluteTimeRange absoluteTimeRange,
            Duration timeShift,
            SimpleTreeBuilder treeBuilder,
            EventProcessorFilter filter) {

        super(TYPES, absoluteTimeRange, timeShift, treeBuilder, filter);
        this.weightExtractor = Type.MALLOC.weight().extractor();
    }

    @Override
    protected SimpleRecord mapEvent(RecordedEvent event) {
        return new SimpleRecord(
                new JdkStackTrace(event.getStackTrace()),
                new JdkThread(event),
                1,
                this.weightExtractor.applyAsLong(event));
    }
}
