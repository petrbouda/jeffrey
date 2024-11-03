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

package pbouda.jeffrey.settings;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.ExecutionSampleType;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class ActiveSettings {

    private static final SettingNameLabel ASYNC_PROFILER_RECORDING =
            new SettingNameLabel(EventTypeName.ACTIVE_RECORDING, "Async-profiler Recording");
    private static final SettingNameLabel JDK_RECORDING =
            new SettingNameLabel(EventTypeName.ACTIVE_RECORDING, "Flight Recording");

    private final Map<SettingNameLabel, ActiveSetting> settings;

    public ActiveSettings(Map<SettingNameLabel, ActiveSetting> settings) {
        this.settings = settings;
    }

    public Collection<ActiveSetting> all() {
        return settings.values();
    }

    public Optional<ActiveSetting> asprofRecording() {
        return Optional.ofNullable(settings.get(ASYNC_PROFILER_RECORDING));
    }

    public Optional<EventSource> allocationSupportedBy() {
        return findByName(EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB)
                .filter(ActiveSetting::isEnabled)
                .map(setting -> setting.getParam("alloc").isPresent() ? EventSource.ASYNC_PROFILER : EventSource.JDK);
    }

    public Optional<EventSource> monitorEnterSupportedBy() {
        return findByName(EventTypeName.JAVA_MONITOR_ENTER)
                .filter(ActiveSetting::isEnabled)
                .map(setting -> setting.getParam("lock").isPresent() ? EventSource.ASYNC_PROFILER : EventSource.JDK);
    }

    public Duration asprofInterval() {
        return asprofRecording()
                .flatMap(setting -> setting.getParam("interval"))
                .map(IntervalParser::parse)
                .orElse(IntervalParser.DEFAULT_INTERVAL);
    }

    public Optional<EventSource> threadParkSupportedBy() {
        Optional<ActiveSetting> settingOpt = findByName(EventTypeName.THREAD_PARK);
        if (settingOpt.isEmpty() || !settingOpt.get().isEnabled()) {
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
    public Optional<ExecutionSampleType> executionSampleType() {
        Optional<String> eventName = asprofRecording()
                .flatMap(setting -> setting.getParam("event"));

        if (eventName.isPresent()) {
            return eventName.map(ExecutionSampleType::resolveAsyncProfilerType);
        } else {
            return findByName(EventTypeName.EXECUTION_SAMPLE)
                    .filter(ActiveSetting::isEnabled)
                    .map(setting -> ExecutionSampleType.EXECUTION_SAMPLE);
        }
    }

    private Optional<ActiveSetting> findByName(String eventName) {
        return settings.values().stream()
                .filter(setting -> setting.event().code().equals(eventName))
                .findFirst();
    }
}
