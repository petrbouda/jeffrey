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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HistogramRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ClassHistogramAnalyzer}.
 *
 * The aggregation runs as a single GROUP BY in DuckDB
 * ({@link HeapView#classHistogram()}), which is the canonical SQL-pushdown
 * win versus the NetBeans path's per-class Java loop.
 *
 * Instances pointing at no class row (primitive arrays, corrupt refs) are
 * folded into a single {@code <unknown>} bucket so callers don't see nulls.
 */
public final class ClassHistogramAnalyzer {

    private static final String UNKNOWN_NAME = "<unknown>";

    private ClassHistogramAnalyzer() {
    }

    public static List<ClassHistogramEntry> analyze(HeapView view, int topN, SortBy sortBy)
            throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }
        if (sortBy == null) {
            throw new IllegalArgumentException("sortBy must not be null");
        }

        List<HistogramRow> rows = view.classHistogram();
        Comparator<ClassHistogramEntry> cmp = switch (sortBy) {
            case SIZE -> Comparator.comparingLong(ClassHistogramEntry::totalSize).reversed();
            case COUNT -> Comparator.comparingLong(ClassHistogramEntry::instanceCount).reversed();
            case CLASS_NAME -> Comparator.comparing(ClassHistogramEntry::className);
        };

        return rows.stream()
                .map(r -> new ClassHistogramEntry(
                        r.className() != null ? r.className() : UNKNOWN_NAME,
                        r.instanceCount(),
                        r.totalShallowSize()))
                .sorted(cmp)
                .limit(topN)
                .toList();
    }
}
