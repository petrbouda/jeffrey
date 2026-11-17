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

import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProfileEventTypeRepository {

    Optional<EventTypeWithFields> singleFieldsByEventType(Type type);

    /**
     * Batch variant of {@link #singleFieldsByEventType(Type)} — one representative fields-row per
     * event type. Implementations are expected to override this with a single query; the default
     * delegates to the single-type lookup, one call per type.
     *
     * @param types event types to look up
     * @return mapping of the event type to its representative fields, absent types are omitted
     */
    default Map<Type, EventTypeWithFields> singleFieldsByEventTypes(List<Type> types) {
        Map<Type, EventTypeWithFields> result = new LinkedHashMap<>();
        for (Type type : types) {
            singleFieldsByEventType(type).ifPresent(fields -> result.put(type, fields));
        }
        return result;
    }

    List<FieldDescription> eventColumns(Type type);

    List<EventSummary> eventSummaries(List<Type> types);

    /**
     * Same as {@link #eventSummaries(List)} but narrowed to a {@link SpanScope} — the per-type
     * sample/weight totals counted only within the windows that scope selects. Calculated (non-DB)
     * summaries are omitted because they cannot be span-scoped.
     */
    List<EventSummary> eventSummaries(List<Type> types, SpanScope spanScope);

    List<EventSummary> eventSummaries();

    default Optional<EventSummary> eventSummaries(Type type) {
        List<EventSummary> summaries = eventSummaries(List.of(type));
        return summaries.isEmpty() ? Optional.empty() : Optional.of(summaries.getFirst());
    }
}
