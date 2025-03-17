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
import pbouda.jeffrey.common.model.EventTypeName;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.time.Instant;

public class RecordingSourceInformationProcessor implements EventProcessor<RecordingSourceInformationProcessor.ProcessingResult> {

    public record ProcessingResult(EventSource eventSource, Instant profilingStart) {
    }

    private RecordingSourceInformationProcessor.ProcessingResult result;

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        String eventName = event.getEventType().getName();
        if (EventTypeName.ACTIVE_RECORDING.equals(eventName)) {
            Instant recordingStart = event.getInstant("recordingStart");

            // Async-Profiler ActiveRecording starts with "async-profiler"
            // and Async-Profiler does not record thread, JDK records main or `JFR Periodic Tasks` threads
            boolean asProfByName = event.getString("name").startsWith("async-profiler");
            if (asProfByName || event.getThread() == null) {
                result = new ProcessingResult(EventSource.ASYNC_PROFILER, recordingStart);
            } else {
                result = new ProcessingResult(EventSource.JDK, recordingStart);
            }
            return Result.DONE;
        }

        return Result.CONTINUE;
    }

    @Override
    public RecordingSourceInformationProcessor.ProcessingResult get() {
        return result;
    }
}
