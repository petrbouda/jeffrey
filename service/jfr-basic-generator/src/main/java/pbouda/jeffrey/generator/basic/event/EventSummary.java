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

package pbouda.jeffrey.generator.basic.event;

import jdk.jfr.EventType;

import java.util.HashMap;
import java.util.Map;

public record EventSummary(EventType eventType, long samples, long weight, Map<String, Object> extras) {

    public EventSummary(EventType eventType, long samples, long weight) {
        this(eventType, samples, weight, Map.of());
    }

    public EventSummary copyAndAddExtras(Map<String, Object> extras) {
        Map<String, Object> newExtras = new HashMap<>(this.extras);
        newExtras.putAll(extras);
        return new EventSummary(eventType, samples, weight, Map.copyOf(newExtras));
    }

    public EventSummary copyAndAddExtra(String key, Object value) {
        Map<String, Object> newExtras = new HashMap<>(this.extras);
        newExtras.put(key, value);
        return new EventSummary(eventType, samples, weight, Map.copyOf(newExtras));
    }
}
