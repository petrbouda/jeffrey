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

package pbouda.jeffrey.common.model.profile;

import pbouda.jeffrey.common.EventSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EventType(
        String name,
        String label,
        String description,
        List<String> categories,
        EventSource source,
        String subtype,
        long samples,
        Long weight,
        Map<String, String> extras) {

    public EventType copyWithWeight(long weight) {
        return new EventType(name, label, description, categories, source, subtype, samples, weight, extras);
    }

    public EventType copyAndAddExtras(Map<String, String> extras) {
        Map<String, String> newExtras = new HashMap<>();
        if (this.extras != null) {
            newExtras.putAll(this.extras);
        }
        newExtras.putAll(extras);
        return new EventType(name, label, description, categories, source, subtype, samples, weight, newExtras);
    }

    public EventType copyWithSubtype(String subtype) {
        return new EventType(name, label, description, categories, source, subtype, samples, weight, extras);
    }

    public EventType copyWithSource(EventSource source) {
        return new EventType(name, label, description, categories, source, subtype, samples, weight, extras);
    }
}
