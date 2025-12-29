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

package pbouda.jeffrey.shared.settings;

import pbouda.jeffrey.shared.model.RecordingEventSource;
import pbouda.jeffrey.shared.model.EventSubtype;
import pbouda.jeffrey.shared.model.Type;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActiveSettings {

    private final Map<String, ActiveSetting> settings;

    public ActiveSettings(Map<String, ActiveSetting> settings) {
        this.settings = settings;
    }

    public ActiveSettings(List<ActiveSetting> settings) {
        this.settings = settings.stream()
                .collect(Collectors.toMap(ActiveSetting::event, Function.identity()));
    }

    public Optional<RecordingEventSource> allocationSupportedBy() {
        return findFirstByType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
                .filter(ActiveSetting::enabled)
                .map(setting -> setting.getParam("alloc").isPresent() ? RecordingEventSource.ASYNC_PROFILER : RecordingEventSource.JDK);
    }

    public Optional<RecordingEventSource> monitorEnterSupportedBy() {
        return findFirstByType(Type.JAVA_MONITOR_ENTER)
                .filter(ActiveSetting::enabled)
                .map(setting -> setting.getParam("lock").isPresent() ? RecordingEventSource.ASYNC_PROFILER : RecordingEventSource.JDK);
    }

    public Optional<RecordingEventSource> threadParkSupportedBy() {
        Optional<ActiveSetting> settingOpt = findFirstByType(Type.THREAD_PARK);
        if (settingOpt.isEmpty() || !settingOpt.get().enabled()) {
            return Optional.empty();
        }

        // Async-Profiler always enables ThreadPark and MonitorEnter together
        Optional<RecordingEventSource> eventSource = monitorEnterSupportedBy();
        if (eventSource.isPresent() && eventSource.get() == RecordingEventSource.ASYNC_PROFILER) {
            return Optional.of(RecordingEventSource.ASYNC_PROFILER);
        } else {
            return Optional.of(RecordingEventSource.JDK);
        }
    }

    /**
     * Resolves the active Execution Sample type. It makes difference between the Async-profiler and JDK recordings.
     * It does not have to be only CPU profiling, but also other types of profiling like ITIMER, CTIMER, WALL,
     * Method tracing etc.
     *
     * @return the active Execution Sample type (JDK or Async-profiler)
     */
    public Optional<EventSubtype> executionSampleType() {
        Optional<String> eventType = findFirstByType(Type.ACTIVE_RECORDING)
                .flatMap(setting -> setting.getParam("event"));

        if (eventType.isPresent()) {
            return eventType.map(EventSubtype::resolveAsyncProfilerType);
        } else {
            return findFirstByType(Type.EXECUTION_SAMPLE)
                    .filter(ActiveSetting::enabled)
                    .map(setting -> EventSubtype.EXECUTION_SAMPLE);
        }
    }

    public Optional<ActiveSetting> findFirstByType(Type eventType) {
        return settings.values().stream()
                .filter(setting -> setting.event().equals(eventType.code()))
                .findFirst();
    }
}
