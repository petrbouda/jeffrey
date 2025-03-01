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

package pbouda.jeffrey.common.model;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventSubtype;
import pbouda.jeffrey.common.Type;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ActiveSettings {

//    private static final SettingNameLabel ASYNC_PROFILER_RECORDING =
//            new SettingNameLabel(EventTypeName.ACTIVE_RECORDING, "Async-profiler Recording");
//    private static final SettingNameLabel JDK_RECORDING =
//            new SettingNameLabel(EventTypeName.ACTIVE_RECORDING, "Flight Recording");

    private final Map<String, ActiveSetting> settings;

    public ActiveSettings(Map<String, ActiveSetting> settings) {
        this.settings = settings;
    }

    public Map<String, ActiveSetting> settingsMap() {
        return settings;
    }

    public Collection<ActiveSetting> all() {
        return settings.values();
    }

    public Optional<ActiveSetting> asprofRecording() {
        throw new UnsupportedOperationException("Not implemented yet");
//        return Optional.ofNullable(settings.get(ASYNC_PROFILER_RECORDING));
    }

    public Optional<EventSource> allocationSupportedBy() {
        return findFirstByType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
                .filter(ActiveSetting::enabled)
                .map(setting -> setting.getParam("alloc").isPresent() ? EventSource.ASYNC_PROFILER : EventSource.JDK);
    }

    public Optional<EventSource> monitorEnterSupportedBy() {
        return findFirstByType(Type.JAVA_MONITOR_ENTER)
                .filter(ActiveSetting::enabled)
                .map(setting -> setting.getParam("lock").isPresent() ? EventSource.ASYNC_PROFILER : EventSource.JDK);
    }

    public Duration asprofInterval() {
        return asprofRecording()
                .flatMap(setting -> setting.getParam("interval"))
                .map(IntervalParser::parse)
                .orElse(IntervalParser.ASYNC_PROFILER_DEFAULT);
    }

    public Optional<EventSource> threadParkSupportedBy() {
        Optional<ActiveSetting> settingOpt = findFirstByType(Type.THREAD_PARK);
        if (settingOpt.isEmpty() || !settingOpt.get().enabled()) {
            return Optional.empty();
        }

        Optional<EventSource> eventSource = monitorEnterSupportedBy();
        if (eventSource.isPresent() && eventSource.get() == EventSource.ASYNC_PROFILER) {
            return Optional.of(EventSource.ASYNC_PROFILER);
        } else {
            return Optional.of(EventSource.JDK);
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
        Optional<String> eventName = asprofRecording()
                .flatMap(setting -> setting.getParam("event"));

        if (eventName.isPresent()) {
            return eventName.map(EventSubtype::resolveAsyncProfilerType);
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

    public List<ActiveSetting> findAllByType(Type eventType) {
        return settings.values().stream()
                .filter(setting -> setting.event().equals(eventType.code()))
                .toList();
    }
}
