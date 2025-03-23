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

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.time.Instant;

public class RecordingSourceInformationProcessor implements EventProcessor<RecordingSourceInformationProcessor.ProcessingResult> {

    public record ProcessingResult(EventSource eventSource, Instant profilingStart) {
    }

    private Instant recordingStart = Instant.MAX;
    private EventSource eventSource = EventSource.JDK;


    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.of(Type.ACTIVE_RECORDING);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant recordingStart = event.getInstant("recordingStart");
        if (recordingStart.isBefore(this.recordingStart)) {
            this.recordingStart = recordingStart;
        }

        // Async-Profiler ActiveRecording starts with "async-profiler"
        // and Async-Profiler does not record thread, JDK records main or `JFR Periodic Tasks` threads
        boolean asProfByName = event.getString("name").startsWith("async-profiler");
        if (asProfByName || event.getThread() == null) {
            eventSource = EventSource.ASYNC_PROFILER;
        }

        return Result.CONTINUE;
    }

    @Override
    public RecordingSourceInformationProcessor.ProcessingResult get() {
        return new ProcessingResult(eventSource, recordingStart);
    }
}
