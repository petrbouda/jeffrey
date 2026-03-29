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
 * Request to compare two class histograms from different heap dumps.
 *
 * @param baseline           class histogram entries from the baseline (older) heap dump
 * @param current            class histogram entries from the current (newer) heap dump
 * @param baselineTotalBytes total heap size in bytes of the baseline heap dump
 * @param currentTotalBytes  total heap size in bytes of the current heap dump
 */
public record HeapDumpComparisonRequest(
        List<ClassHistogramEntry> baseline,
        List<ClassHistogramEntry> current,
        long baselineTotalBytes,
        long currentTotalBytes
) {
}
