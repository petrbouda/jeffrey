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

package pbouda.jeffrey.profile.summary.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EventSummary(
        String name,
        String label,
        long samples,
        long weight,
        boolean hasStacktrace,
        List<String> categories,
        Map<String, String> extras) {

    public EventSummary copyWithWeight(long weight) {
        return new EventSummary(name, label, samples, weight, hasStacktrace, categories, extras);
    }

    public EventSummary copyAndAddExtras(Map<String, String> extras) {
        Map<String, String> newExtras = new HashMap<>(this.extras);
        newExtras.putAll(extras);
        return new EventSummary(name, label, samples, weight, hasStacktrace, categories, Map.copyOf(newExtras));
    }

    public EventSummary copyAndAddExtra(String key, String value) {
        Map<String, String> newExtras = new HashMap<>(this.extras);
        newExtras.put(key, value);
        return new EventSummary(name, label, samples, weight, hasStacktrace, categories, Map.copyOf(newExtras));
    }
}
