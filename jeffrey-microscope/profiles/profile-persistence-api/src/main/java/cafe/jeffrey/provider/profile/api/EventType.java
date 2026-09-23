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

package cafe.jeffrey.provider.profile.api;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.Type;

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
