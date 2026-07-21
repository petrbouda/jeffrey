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

package cafe.jeffrey.provider.profile.api;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Map;

public record EventType(
        String name,
        String label,
        Long typeId,
        String description,
        List<String> categories,
        JsonNode columns,
        Map<String, String> extras,
        /**
         * The event's source, set explicitly by format-specific readers (pprof / OTLP) whose codes carry
         * no namespace to infer it from. {@code null} for JFR, where the collector derives it from the
         * {@code jdk.}/{@code profiler.} code namespace.
         */
        RecordingEventSource source) {

    /** Convenience constructor for readers that attach no import-time extras and leave source name-derived. */
    public EventType(String name, String label, Long typeId, String description,
                     List<String> categories, JsonNode columns) {
        this(name, label, typeId, description, categories, columns, Map.of(), null);
    }

    /** Convenience constructor with extras but source left name-derived. */
    public EventType(String name, String label, Long typeId, String description,
                     List<String> categories, JsonNode columns, Map<String, String> extras) {
        this(name, label, typeId, description, categories, columns, extras, null);
    }

    public Type type() {
        return Type.fromCode(name);
    }
}
