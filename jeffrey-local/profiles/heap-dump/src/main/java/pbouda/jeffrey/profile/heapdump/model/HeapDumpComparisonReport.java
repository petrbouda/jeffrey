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

import java.util.List;

/**
 * Report comparing class histograms from two heap dumps.
 *
 * @param baselineTotalBytes total heap size in bytes of the baseline heap dump
 * @param currentTotalBytes  total heap size in bytes of the current heap dump
 * @param totalBytesDelta    difference in total heap size (current - baseline)
 * @param baselineClassCount number of classes with instances in the baseline heap dump
 * @param currentClassCount  number of classes with instances in the current heap dump
 * @param entries            per-class comparison entries sorted by absolute size delta descending
 */
public record HeapDumpComparisonReport(
        long baselineTotalBytes,
        long currentTotalBytes,
        long totalBytesDelta,
        int baselineClassCount,
        int currentClassCount,
        List<ClassComparisonEntry> entries
) {
}
