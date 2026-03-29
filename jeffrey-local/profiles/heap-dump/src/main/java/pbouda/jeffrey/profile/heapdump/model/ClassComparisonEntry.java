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

package pbouda.jeffrey.profile.heapdump.model;

/**
 * Represents the comparison of a single class between two heap dumps.
 *
 * @param className     fully qualified class name
 * @param baselineSize  total size in bytes in the baseline heap dump
 * @param currentSize   total size in bytes in the current heap dump
 * @param sizeDelta     difference in bytes (current - baseline)
 * @param baselineCount instance count in the baseline heap dump
 * @param currentCount  instance count in the current heap dump
 * @param countDelta    difference in instance count (current - baseline)
 * @param status        comparison status: GREW, SHRANK, NEW, REMOVED, or UNCHANGED
 */
public record ClassComparisonEntry(
        String className,
        long baselineSize,
        long currentSize,
        long sizeDelta,
        long baselineCount,
        long currentCount,
        long countDelta,
        ComparisonStatus status
) {

    /**
     * Status of a class in the heap dump comparison.
     */
    public enum ComparisonStatus {
        /** Class grew in size or instance count */
        GREW,
        /** Class shrank in size or instance count */
        SHRANK,
        /** Class exists only in the current heap dump */
        NEW,
        /** Class exists only in the baseline heap dump */
        REMOVED,
        /** Class did not change between heap dumps */
        UNCHANGED
    }
}
