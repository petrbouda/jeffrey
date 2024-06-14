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
import pbouda.jeffrey.generator.flamegraph.record.ExecutionSampleRecord;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.time.Instant;

public class BasicSampleEventProcessor extends StacktraceBasedEventProcessor<StackBasedRecord> {

    public BasicSampleEventProcessor(Type eventType, AbsoluteTimeRange absoluteTimeRange) {
        super(eventType, absoluteTimeRange);
    }

    @Override
    protected ExecutionSampleRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        if (event.hasField("sampledThread")) {
            return new ExecutionSampleRecord(
                    modifiedEventTime,
                    event.getStackTrace(),
                    event.getThread("sampledThread"));
        } else {
            return new ExecutionSampleRecord(
                    modifiedEventTime,
                    event.getStackTrace(),
                    null);
        }
    }
}
