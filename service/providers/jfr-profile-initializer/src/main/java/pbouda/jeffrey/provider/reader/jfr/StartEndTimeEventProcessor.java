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
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.time.Instant;

public class StartEndTimeEventProcessor implements EventProcessor<ProfilingStartEnd> {

    private Instant startTime;
    private Instant latestEvent;

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        if (Type.from(event.getEventType()) == Type.ACTIVE_RECORDING) {
            this.startTime = resolveRecordingStart(event);
        }

        this.latestEvent = resolveLatestEvent(event);

        return Result.CONTINUE;
    }

    private Instant resolveLatestEvent(RecordedEvent event) {
        Instant endTime = event.getEndTime();
        if (endTime == null || endTime == Instant.MIN || endTime == Instant.MAX) {
            return latestEvent;
        }

        return latestEvent == null || endTime.isAfter(latestEvent) ? endTime : latestEvent;
    }

    private Instant resolveRecordingStart(RecordedEvent event) {
        Instant recordingStart = event.getInstant("recordingStart");
        if (recordingStart == Instant.MIN || recordingStart == null) {
            return this.startTime;
        }

        return startTime == null || startTime.isAfter(recordingStart) ? recordingStart : startTime;
    }

    @Override
    public ProfilingStartEnd get() {
        return new ProfilingStartEnd(startTime, latestEvent);
    }
}
