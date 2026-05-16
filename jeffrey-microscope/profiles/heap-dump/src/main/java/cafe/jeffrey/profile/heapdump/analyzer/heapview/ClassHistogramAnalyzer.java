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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ReferrerSummary;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HistogramRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * <p>
 * The aggregation runs as a single GROUP BY in DuckDB
 * ({@link HeapView#classHistogram()}), which is the canonical SQL-pushdown
 * win versus the NetBeans path's per-class Java loop.
 * <p>
 * Instances pointing at no class row (primitive arrays, corrupt refs) are
 * folded into a single {@code <unknown>} bucket so callers don't see nulls.
 */
public final class ClassHistogramAnalyzer {

    private static final String UNKNOWN_NAME = "<unknown>";

    /**
     * Class names whose instances are too opaque to be self-describing — knowing
     * "this is a byte[]" doesn't tell you whether it backs a String, a HeapByteBuffer,
     * a deserialized cache entry, etc. For these rows we surface the top distinct
     * referrer class names so the UI can render a "referrers" badge row.
     */
    private static final Set<String> REFERRER_HINT_CLASS_NAMES = Set.of(byte[].class.getSimpleName());

    /** Max distinct referrer class names to attach per opaque histogram row. */
    private static final int REFERRER_HINT_LIMIT = 3;

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

        List<ClassHistogramEntry> entries = rows.stream()
                .map(r -> new ClassHistogramEntry(
                        r.className() != null ? r.className() : UNKNOWN_NAME,
                        r.instanceCount(),
                        r.totalShallowSize(),
                        List.<ReferrerSummary>of()))
                .sorted(cmp)
                .limit(topN)
                .toList();

        return enrichWithReferrers(view, entries);
    }

    /**
     * For each entry whose class is opaque (e.g. {@code byte[]}), compute the top N
     * referrer classes by sum-of-bytes attributable to each, and re-wrap the entry
     * with the populated referrer hint list.
     */
    private static List<ClassHistogramEntry> enrichWithReferrers(
            HeapView view, List<ClassHistogramEntry> entries) throws SQLException {
        List<ClassHistogramEntry> enriched = new ArrayList<>(entries.size());
        for (ClassHistogramEntry e : entries) {
            if (!REFERRER_HINT_CLASS_NAMES.contains(e.className()) || e.totalSize() <= 0) {
                enriched.add(e);
                continue;
            }
            Long classId = findClassId(view, e.className());
            if (classId == null) {
                enriched.add(e);
                continue;
            }
            List<ReferrerSummary> top = loadTopReferrers(view, classId, e.totalSize());
            enriched.add(new ClassHistogramEntry(e.className(), e.instanceCount(), e.totalSize(), top));
        }
        return enriched;
    }

    /** Resolve the class id for a given class name; {@code null} when not present. */
    private static Long findClassId(HeapView view, String className) throws SQLException {
        List<JavaClassRow> classes = view.findClassesByName(className);
        if (classes.isEmpty()) {
            return null;
        }
        return classes.get(0).classId();
    }

    /**
     * For each instance of the opaque class, pick a deterministic representative
     * referrer (lowest source_id by outbound_ref ordering) and attribute the
     * instance's shallow size to that referrer's class. Sum across classes,
     * return the top {@link #REFERRER_HINT_LIMIT} by attributed bytes.
     */
    private static List<ReferrerSummary> loadTopReferrers(HeapView view, long classId, long totalBytes)
            throws SQLException {
        String sql = """
                WITH ranked AS (
                    SELECT
                        o.target_id,
                        src_i.class_id AS source_class_id,
                        ROW_NUMBER() OVER (PARTITION BY o.target_id ORDER BY o.source_id) AS rn
                    FROM outbound_ref o
                    JOIN instance src_i ON src_i.instance_id = o.source_id
                    WHERE o.target_id IN (SELECT instance_id FROM instance WHERE class_id = ?)
                ),
                dominant AS (
                    SELECT target_id, source_class_id FROM ranked WHERE rn = 1
                )
                SELECT c.name AS referrer_class, SUM(i.shallow_size) AS attributed_bytes
                FROM dominant d
                JOIN instance i ON i.instance_id = d.target_id
                LEFT JOIN class c ON c.class_id = d.source_class_id
                GROUP BY c.name
                ORDER BY attributed_bytes DESC, c.name
                LIMIT ?
                """;
        List<ReferrerSummary> out = new ArrayList<>(REFERRER_HINT_LIMIT);
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setLong(1, classId);
            stmt.setInt(2, REFERRER_HINT_LIMIT);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    long bytes = rs.getLong(2);
                    if (name == null) {
                        name = UNKNOWN_NAME;
                    }
                    double percent = totalBytes == 0 ? 0.0 : (bytes * 100.0) / totalBytes;
                    out.add(new ReferrerSummary(name, percent));
                }
            }
        }
        return List.copyOf(out);
    }
}
