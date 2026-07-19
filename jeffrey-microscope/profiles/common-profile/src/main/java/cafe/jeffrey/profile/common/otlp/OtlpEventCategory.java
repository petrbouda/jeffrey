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

package cafe.jeffrey.profile.common.otlp;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.Locale;
import java.util.Set;

/**
 * The logical flamegraph category an OpenTelemetry (OTLP) event type belongs to. OTLP names its sample
 * dimensions differently from JFR (a CPU profile is {@code otel.cpu} / {@code otel.samples}, not
 * {@code jdk.ExecutionSample}), so the mapping from a concrete event type to a logical category is
 * format-specific. This enum is the OTLP side of that mapping; the OTLP flamegraph controller stamps it
 * onto each event summary so the UI can group cards without hard-coding OTLP codes.
 */
public enum OtlpEventCategory {
    EXECUTION,
    ALLOCATION,
    BLOCKING,
    WALL,
    OTHER;

    private static final Set<String> EXECUTION_TYPES = Set.of("cpu", "samples");
    private static final Set<String> WALL_TYPES = Set.of("wall");
    private static final Set<String> ALLOCATION_TYPES = Set.of("alloc", "allocations", "alloc_space", "alloc_objects");
    private static final Set<String> BLOCKING_TYPES = Set.of("lock", "block", "mutex", "contentions", "delay");

    /**
     * @param eventTypeCode a full OTLP event-type code (e.g. {@code otel.cpu})
     * @return the logical category, or {@link #OTHER} for non-OTLP codes and unrecognized dimensions
     */
    public static OtlpEventCategory resolve(String eventTypeCode) {
        if (eventTypeCode == null || !eventTypeCode.startsWith(EventTypeName.OTEL_NAMESPACE)) {
            return OTHER;
        }
        String dimension = eventTypeCode.substring(EventTypeName.OTEL_NAMESPACE.length()).toLowerCase(Locale.ROOT);
        if (EXECUTION_TYPES.contains(dimension)) {
            return EXECUTION;
        }
        if (ALLOCATION_TYPES.contains(dimension)) {
            return ALLOCATION;
        }
        if (BLOCKING_TYPES.contains(dimension)) {
            return BLOCKING;
        }
        if (WALL_TYPES.contains(dimension)) {
            return WALL;
        }
        return OTHER;
    }
}
