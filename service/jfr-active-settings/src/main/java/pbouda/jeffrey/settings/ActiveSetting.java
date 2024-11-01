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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ActiveSetting {

    private final String event;
    private final Map<String, String> params = new HashMap<>();

    public ActiveSetting(String event) {
        this.event = event;
    }

    public String event() {
        return event;
    }

    public Map<String, String> params() {
        return Collections.unmodifiableMap(params);
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(params.get("enabled"));
    }

    public boolean containsEnabled() {
        return params.containsKey("enabled");
    }

    public void putParam(String key, String value) {
        params.put(key, value);
    }

    @Override
    public String toString() {
        return "ActiveSetting{" + "event=" + event + ", params=" + params +"}";
    }
}
