/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.shared.common.model;

/**
 * Equality filter on a single top-level JSON field of an event ({@code events.fields} column).
 * Carried on graph parameters and pushed down into SQL by the persistence layer.
 * <p>
 * The canonical field names for the OpenTelemetry trace correlation ({@link #TRACE_ID_FIELD},
 * {@link #SPAN_ID_FIELD}) are defined here as the single source of truth — the OTLP parser writes
 * them and the flamegraph/timeseries filters read them.
 */
public record JsonFieldFilter(String field, String value) {

    /**
     * Per-event JSON field carrying the OpenTelemetry trace id (lowercase hex) of the sample's link.
     */
    public static final String TRACE_ID_FIELD = "trace_id";

    /**
     * Per-event JSON field carrying the OpenTelemetry span id (lowercase hex) of the sample's link.
     */
    public static final String SPAN_ID_FIELD = "span_id";

    public JsonFieldFilter {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("JSON field name must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JSON field value must not be blank");
        }
    }

    public static JsonFieldFilter byTraceId(String traceId) {
        return new JsonFieldFilter(TRACE_ID_FIELD, traceId);
    }

    public static JsonFieldFilter bySpanId(String spanId) {
        return new JsonFieldFilter(SPAN_ID_FIELD, spanId);
    }
}
