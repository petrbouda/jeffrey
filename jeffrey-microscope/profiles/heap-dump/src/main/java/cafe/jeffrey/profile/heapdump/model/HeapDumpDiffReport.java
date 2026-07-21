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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Class-histogram comparison of two heap dumps — the classic before/after
 * leak workflow: what grew, what appeared, what shrank between a baseline
 * dump and the current one.
 *
 * @param primarySummary   summary of the primary (current) dump
 * @param baselineSummary  summary of the baseline dump
 * @param instanceCountDelta total instance-count difference (primary - baseline)
 * @param shallowBytesDelta  total shallow-bytes difference (primary - baseline)
 * @param entries          per-class deltas ordered by absolute shallow-bytes
 *                         delta descending, capped at the requested topN
 */
public record HeapDumpDiffReport(
        HeapSummary primarySummary,
        HeapSummary baselineSummary,
        long instanceCountDelta,
        long shallowBytesDelta,
        List<ClassDiffEntry> entries
) {
}
