/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.model.settings;

import cafe.jeffrey.microscope.model.EventSubtype;
import cafe.jeffrey.microscope.model.Type;

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

    /**
     * Resolves the active Execution Sample type. An async-profiler recording names its
     * profiling mode in the {@code event} parameter of {@code jdk.ActiveRecording} (CPU, ITIMER, CTIMER,
     * WALL, method tracing, …); without it the samples are the JDK's own method profiling samples.
     *
     * @return the active Execution Sample type
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
