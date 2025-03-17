/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.jfrparser.jdk.Collector;
import pbouda.jeffrey.provider.reader.jfr.RecordingSourceInformationProcessor.ProcessingResult;

import java.time.Instant;
import java.util.function.Supplier;

public class RecordingSourceInformationCollector implements Collector<ProcessingResult, ProcessingResult> {
    @Override
    public Supplier<ProcessingResult> empty() {
        return () -> new ProcessingResult(EventSource.JDK, Instant.MAX);
    }

    @Override
    public ProcessingResult combiner(ProcessingResult partial1, ProcessingResult partial2) {
        return new ProcessingResult(
                resolveEventSource(partial1, partial2),
                resolveRecordingStart(partial1, partial2));
    }

    private static EventSource resolveEventSource(ProcessingResult partial1, ProcessingResult partial2) {
        return partial1.eventSource() == EventSource.JDK && partial2.eventSource() == EventSource.JDK
                ? EventSource.JDK
                : EventSource.ASYNC_PROFILER;
    }

    private static Instant resolveRecordingStart(ProcessingResult start1, ProcessingResult start2) {
        return start1.profilingStart().isBefore(start2.profilingStart())
                ? start1.profilingStart()
                : start2.profilingStart();
    }

    @Override
    public ProcessingResult finisher(ProcessingResult combined) {
        return combined;
    }
}
