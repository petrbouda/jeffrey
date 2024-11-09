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

package pbouda.jeffrey.profile.settings;

import pbouda.jeffrey.common.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates a new instance of ActiveSetting.
 *
 * @param event  the event name (e.g. "jdk.ExecutionSample")
 * @param label  the label of the event (e.g. "Execution Sample")
 * @param params the parameters of the event
 */

public record ActiveSetting(Type event, String label, Map<String, String> params) {

    /**
     * Creates a new instance of ActiveSetting.
     *
     * @param event the event name (e.g. "jdk.ExecutionSample")
     * @param label the label of the event (e.g. "Execution Sample")
     */
    public ActiveSetting(Type event, String label) {
        this(event, label, new HashMap<>());
    }

    public boolean enabled() {
        return Boolean.parseBoolean(params.get("enabled"));
    }

    public void putParam(String key, String value) {
        params.put(key, value);
    }

    public Optional<String> getParam(String key) {
        String value = params.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
