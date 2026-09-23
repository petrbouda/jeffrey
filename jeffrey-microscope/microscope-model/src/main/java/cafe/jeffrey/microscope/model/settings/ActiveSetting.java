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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates a new instance of ActiveSetting.
 *
 * @param event  the event name (e.g. "jdk.ExecutionSample")
 * @param params the parameters of the event
 */

public record ActiveSetting(String event, Map<String, String> params) {

    public ActiveSetting(String event, Map<String, String> params) {
        this.event = event;
        this.params = params != null ? params : new HashMap<>();
    }

    /**
     * Creates a new instance of ActiveSetting.
     *
     * @param event the event name (e.g. "jdk.ExecutionSample")
     */
    public ActiveSetting(String event) {
        this(event, new HashMap<>());
    }

    public boolean enabled() {
        // Async-Profilers ActiveRecording and ExecutionSample does not have `enabled` field
        return Boolean.parseBoolean(params.get("enabled")) || params.get("enabled") == null;
    }

    public void putParam(String key, String value) {
        params.put(key, value);
    }

    public Optional<String> getParam(String key) {
        String value = params.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
