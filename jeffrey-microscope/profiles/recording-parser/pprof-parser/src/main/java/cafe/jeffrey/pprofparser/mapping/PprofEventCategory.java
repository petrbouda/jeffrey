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

package cafe.jeffrey.pprofparser.mapping;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.Locale;
import java.util.Set;

/**
 * The logical flamegraph category a pprof event type belongs to. pprof names its sample dimensions
 * differently from JFR (a CPU profile is {@code pprof.cpu} / {@code pprof.samples}, not
 * {@code jdk.ExecutionSample}), so the mapping from a concrete event type to a logical category is
 * format-specific. This enum is the single owner of the pprof dimension vocabulary — both the
 * event-type naming at ingest time and the category stamping on event summaries resolve through it.
 */
public enum PprofEventCategory {
    EXECUTION,
    ALLOCATION,
    BLOCKING,
    WALL,
    OTHER;

    private static final Set<String> EXECUTION_TYPES = Set.of("cpu", "samples");
    private static final Set<String> WALL_TYPES = Set.of("wall");
    private static final Set<String> ALLOCATION_TYPES = Set.of(
            "alloc_space", "alloc_objects", "inuse_space", "inuse_objects",
            "allocations", "space", "alloc", "inuse");
    private static final Set<String> BLOCKING_TYPES = Set.of("contentions", "delay", "block", "mutex");

    /**
     * @param eventTypeCode a full pprof event-type code (e.g. {@code pprof.cpu})
     * @return the logical category, or {@link #OTHER} for non-pprof codes and unrecognized dimensions
     */
    public static PprofEventCategory resolve(String eventTypeCode) {
        if (eventTypeCode == null || !eventTypeCode.startsWith(EventTypeName.PPROF_NAMESPACE)) {
            return OTHER;
        }
        return ofDimension(eventTypeCode.substring(EventTypeName.PPROF_NAMESPACE.length()));
    }

    /**
     * @param dimension a bare pprof sample-type dimension (e.g. {@code cpu}, {@code alloc_space})
     * @return the logical category, or {@link #OTHER} for unrecognized dimensions
     */
    public static PprofEventCategory ofDimension(String dimension) {
        String lower = dimension.toLowerCase(Locale.ROOT);
        if (EXECUTION_TYPES.contains(lower)) {
            return EXECUTION;
        }
        if (ALLOCATION_TYPES.contains(lower)) {
            return ALLOCATION;
        }
        if (BLOCKING_TYPES.contains(lower)) {
            return BLOCKING;
        }
        if (WALL_TYPES.contains(lower)) {
            return WALL;
        }
        return OTHER;
    }
}
