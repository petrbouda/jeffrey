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

package pbouda.jeffrey.guardian.preconditions;

import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.util.List;

public class GuardRecordingInformationEventProcessor implements EventProcessor<GuardRecordingInformation> {

    private static final Logger LOG = LoggerFactory.getLogger(GuardRecordingInformationEventProcessor.class);

    private final GuardRecordingInformation recordingInfo = new GuardRecordingInformation();

    @Override
    public ProcessableEvents processableEvents() {
        List<Type> events = List.of(
                Type.ACTIVE_SETTING,
                Type.ACTIVE_RECORDING,
                Type.GC_CONFIGURATION,
                Type.GC_HEAP_CONFIGURATION);

        return new ProcessableEvents(events);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        if (Type.ACTIVE_RECORDING.sameAs(event) && recordingInfo.getEventSource() == null) {
            String name = event.getString("name");
            EventSource source = name.startsWith("async-profiler") ? EventSource.ASYNC_PROFILER : EventSource.JDK;
            recordingInfo.setEventSource(source);
        } else if (Type.ACTIVE_SETTING.sameAs(event)) {
            String nameValue = event.getString("name");
            if ("debugSymbols".equals(nameValue)) {
                recordingInfo.setDebugSymbolsAvailable(safeParseBoolean(event.getString("value")));
            } else if ("kernelSymbols".equals(nameValue)) {
                recordingInfo.setKernelSymbolsAvailable(safeParseBoolean(event.getString("value")));
            }
        } else if (Type.GC_CONFIGURATION.sameAs(event) && recordingInfo.getGarbageCollectorType() == null) {
            String oldCollector = event.getString("oldCollector");
            GarbageCollectorType oldGC = GarbageCollectorType.fromOldGenCollector(oldCollector);
            if (oldGC == null) {
                LOG.warn("Unknown Old Generation Garbage Collector: {}", oldCollector);
            } else {
                recordingInfo.setGarbageCollectorType(oldGC);
            }
        }

        return recordingInfo.isCompleted() ? Result.DONE : Result.CONTINUE;
    }

    private static Boolean safeParseBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : null;
    }

    @Override
    public GuardRecordingInformation get() {
        return recordingInfo;
    }
}
