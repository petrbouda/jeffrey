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

import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.Type;

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
     * Same as {@link #eventSummaries(List)} but scoped to the union of the given span (thread, window)
     * intervals — the per-type sample/weight totals counted only within those spans. Calculated
     * (non-DB) summaries are omitted because they cannot be span-scoped.
     */
    List<EventSummary> eventSummaries(List<Type> types, List<SpanInterval> spanIntervals);

    List<EventSummary> eventSummaries();

    default Optional<EventSummary> eventSummaries(Type type) {
        List<EventSummary> summaries = eventSummaries(List.of(type));
        return summaries.isEmpty() ? Optional.empty() : Optional.of(summaries.getFirst());
    }
}
