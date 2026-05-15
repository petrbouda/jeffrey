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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.JvmStringFlag;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.StringDeduplicationEntry;
import cafe.jeffrey.profile.heapdump.model.StringInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.StringTopEntry;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;
import cafe.jeffrey.profile.heapdump.parser.JdkFieldNames;

/**
 * Bulk-SQL String analysis.
 *
 * <p>Two aggregations against the index — no per-array mmap reads:
 * <ul>
 *   <li><strong>Physical-sharing</strong>: one row per {@code String.value}
 *       backing array, with its reference count and shallow size. Drives the
 *       "already deduplicated" report and all scalar totals.</li>
 *   <li><strong>Content-sharing</strong>: one row per distinct
 *       {@code string_content.content} that appears in more than one distinct
 *       backing array. Drives the "deduplication opportunities" report and
 *       {@code potentialSavings}.</li>
 * </ul>
 *
 * <p>Strings whose decoded content exceeded the indexer's content cap are
 * stored with {@code content IS NULL}; they contribute to physical-sharing
 * totals (via {@code outbound_ref} / {@code instance}) but are excluded from
 * the content-sharing dedup pass. On realistic heaps this is a tiny fraction
 * of Strings — typical caps cover well over 99% of instances.
 *
 * <p>Top-N previews are pulled from {@code string_content} via
 * {@link HeapView#findStringContent(long)} keyed by a sample String id, so
 * each preview costs at most one PK lookup.
 *
 * <p>JVM-flag enrichment from JFR events stays the caller's job — this
 * analyzer returns an empty {@link JvmStringFlag} list.
 */
public final class StringAnalyzer {

    private static final int DEFAULT_TOP_N = 100;
    private static final int PREVIEW_MAX_CHARS = 200;

    /**
     * Per-backing-array aggregation: one row per distinct {@code value}-array
     * referenced by any {@code String}. {@code 1 = field_id} of String.value,
     * {@code 2 = String's class_id}.
     */
    private static final String PHYSICAL_SHARING_SQL = """
            SELECT
                ref.target_id                AS array_id,
                MAX(arr.shallow_size)        AS array_shallow,
                COUNT(*)                     AS ref_count,
                SUM(CAST(s.shallow_size AS BIGINT)) AS sum_string_shallow,
                MIN(ref.source_id)           AS sample_string_id
            FROM instance s
            JOIN outbound_ref ref ON ref.source_id = s.instance_id AND ref.field_id = ?
            JOIN instance arr     ON arr.instance_id = ref.target_id
            WHERE s.class_id = ?
            GROUP BY ref.target_id
            """;

    /**
     * Content-dedup aggregation: one row per distinct decoded String content
     * that exists in more than one backing array. {@code 1 = field_id} of
     * String.value, {@code 2 = String's class_id}.
     */
    private static final String CONTENT_DEDUP_SQL = """
            SELECT
                sc.content                       AS content,
                COUNT(*)                         AS string_count,
                COUNT(DISTINCT ref.target_id)    AS distinct_arrays,
                MAX(arr.shallow_size)            AS array_shallow,
                MIN(sc.instance_id)              AS sample_string_id
            FROM string_content sc
            JOIN instance s       ON s.instance_id = sc.instance_id
            JOIN outbound_ref ref ON ref.source_id = sc.instance_id AND ref.field_id = ?
            JOIN instance arr     ON arr.instance_id = ref.target_id
            WHERE sc.content IS NOT NULL
              AND s.class_id = ?
            GROUP BY sc.content
            HAVING COUNT(DISTINCT ref.target_id) > 1
            """;

    /**
     * Top-instances aggregation: one row per individual {@code String}
     * instance, pre-ranked by sharing-aware GC retained size and capped to
     * top-N in SQL. A String's backing array contributes to its retained set
     * only when this String is the array's sole referrer (counted via a
     * grouped subquery on {@code outbound_ref}).
     * {@code 1 = field_id} of String.value (for the subquery),
     * {@code 2 = String's class_id} (for the subquery),
     * {@code 3 = field_id} of String.value (for the outer join),
     * {@code 4 = String's class_id} (for the outer filter),
     * {@code 5 = LIMIT}.
     */
    private static final String TOP_INSTANCES_SQL = """
            WITH array_refs AS (
                SELECT ref.target_id AS array_id, COUNT(*) AS ref_count
                FROM instance s
                JOIN outbound_ref ref ON ref.source_id = s.instance_id AND ref.field_id = ?
                WHERE s.class_id = ?
                GROUP BY ref.target_id
            )
            SELECT
                s.instance_id                              AS string_id,
                sc.content                                 AS content,
                arr.shallow_size                           AS array_shallow,
                array_refs.ref_count                       AS array_ref_count,
                s.shallow_size + CASE WHEN array_refs.ref_count = 1
                                      THEN arr.shallow_size
                                      ELSE 0 END           AS retained
            FROM instance s
            JOIN outbound_ref ref       ON ref.source_id = s.instance_id AND ref.field_id = ?
            JOIN instance arr           ON arr.instance_id = ref.target_id
            JOIN array_refs             ON array_refs.array_id = ref.target_id
            LEFT JOIN string_content sc ON sc.instance_id = s.instance_id
            WHERE s.class_id = ?
            ORDER BY retained DESC
            LIMIT ?
            """;

    /**
     * Top-by-retained aggregation: one row per distinct decoded String content,
     * pre-ranked by total retained bytes and capped to top-N in SQL.
     * Retained = sum of String wrapper shallow sizes plus the shallow size of
     * each distinct backing {@code byte[]} array. {@code 1 = field_id} of
     * String.value, {@code 2 = String's class_id}, {@code 3 = LIMIT}.
     */
    private static final String TOP_BY_RETAINED_SQL = """
            SELECT
                sc.content                                  AS content,
                COUNT(*)                                    AS string_count,
                SUM(CAST(s.shallow_size AS BIGINT))         AS sum_string_shallow,
                COUNT(DISTINCT ref.target_id)               AS distinct_arrays,
                MAX(arr.shallow_size)                       AS array_shallow,
                MIN(sc.instance_id)                         AS sample_string_id
            FROM string_content sc
            JOIN instance s       ON s.instance_id = sc.instance_id
            JOIN outbound_ref ref ON ref.source_id = sc.instance_id AND ref.field_id = ?
            JOIN instance arr     ON arr.instance_id = ref.target_id
            WHERE sc.content IS NOT NULL
              AND s.class_id = ?
            GROUP BY sc.content
            ORDER BY (SUM(CAST(s.shallow_size AS BIGINT))
                      + COUNT(DISTINCT ref.target_id) * MAX(arr.shallow_size)) DESC
            LIMIT ?
            """;

    private StringAnalyzer() {
    }

    public static StringAnalysisReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static StringAnalysisReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        List<JavaClassRow> stringClasses = view.findClassesByName(String.class.getName());
        if (stringClasses.isEmpty()) {
            return emptyReport();
        }

        List<PhysicalSharingRow> physRows = new ArrayList<>();
        Map<String, ContentSharingRow> contentRows = new HashMap<>();
        Map<String, TopRetainedRow> topRows = new HashMap<>();
        List<StringInstanceEntry> topInstanceCandidates = new ArrayList<>();
        long totalStrings = 0;
        long totalShallowSize = 0;

        for (JavaClassRow stringClass : stringClasses) {
            int valueFieldId = findValueFieldId(view, stringClass.classId());
            if (valueFieldId < 0) {
                continue;
            }

            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(PHYSICAL_SHARING_SQL)) {
                stmt.setInt(1, valueFieldId);
                stmt.setLong(2, stringClass.classId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long arrayShallow = rs.getLong(2);
                        long refCount = rs.getLong(3);
                        long sumStringShallow = rs.getLong(4);
                        long sampleStringId = rs.getLong(5);
                        physRows.add(new PhysicalSharingRow(arrayShallow, refCount, sampleStringId));
                        totalStrings += refCount;
                        totalShallowSize += sumStringShallow;
                    }
                }
            }

            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(CONTENT_DEDUP_SQL)) {
                stmt.setInt(1, valueFieldId);
                stmt.setLong(2, stringClass.classId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String content = rs.getString(1);
                        long stringCount = rs.getLong(2);
                        long distinctArrays = rs.getLong(3);
                        long arrayShallow = rs.getLong(4);
                        long sampleStringId = rs.getLong(5);
                        // Merge across String classes — same content stored under
                        // different class-loader String classes should coalesce.
                        contentRows.merge(content,
                                new ContentSharingRow(stringCount, distinctArrays, arrayShallow, sampleStringId),
                                ContentSharingRow::merge);
                    }
                }
            }

            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(TOP_BY_RETAINED_SQL)) {
                stmt.setInt(1, valueFieldId);
                stmt.setLong(2, stringClass.classId());
                stmt.setInt(3, topN);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String content = rs.getString(1);
                        long stringCount = rs.getLong(2);
                        long sumStringShallow = rs.getLong(3);
                        long distinctArrays = rs.getLong(4);
                        long arrayShallow = rs.getLong(5);
                        topRows.merge(content,
                                new TopRetainedRow(stringCount, sumStringShallow, distinctArrays, arrayShallow),
                                TopRetainedRow::merge);
                    }
                }
            }

            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(TOP_INSTANCES_SQL)) {
                stmt.setInt(1, valueFieldId);
                stmt.setLong(2, stringClass.classId());
                stmt.setInt(3, valueFieldId);
                stmt.setLong(4, stringClass.classId());
                stmt.setInt(5, topN);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long stringId = rs.getLong(1);
                        String content = rs.getString(2);
                        long arrayShallow = rs.getLong(3);
                        int arrayRefCount = rs.getInt(4);
                        long retained = rs.getLong(5);
                        // Strings whose content exceeded the indexer's cap have
                        // NULL content in the index — read just the prefix from
                        // the mmap so a 100 MB log line costs a 200-byte read,
                        // not a full-array allocation.
                        String preview = content != null
                                ? truncate(content)
                                : decodeOverCapPreview(view, stringId);
                        topInstanceCandidates.add(new StringInstanceEntry(
                                preview,
                                stringId,
                                arrayShallow,
                                arrayRefCount,
                                retained));
                    }
                }
            }
        }

        long uniqueArrays = physRows.size();
        long sharedArrays = physRows.stream().filter(r -> r.refCount > 1).count();
        long totalSharedStrings = physRows.stream().filter(r -> r.refCount > 1)
                .mapToLong(r -> r.refCount).sum();
        long memorySavedByDedup = physRows.stream().filter(r -> r.refCount > 1)
                .mapToLong(r -> (r.refCount - 1) * r.arrayShallowSize).sum();

        List<StringDeduplicationEntry> already = topNByPhysicalSavings(physRows, topN, view);

        long potentialSavings = contentRows.values().stream()
                .mapToLong(c -> (c.distinctArrays - 1) * c.arrayShallowSize)
                .sum();
        List<StringDeduplicationEntry> opps = topNByContentSavings(contentRows, topN);
        List<StringTopEntry> top = topNByRetained(topRows, topN);
        List<StringInstanceEntry> topInstances = topNInstances(topInstanceCandidates, topN);

        return new StringAnalysisReport(
                totalStrings,
                totalShallowSize,
                uniqueArrays,
                sharedArrays,
                totalSharedStrings,
                memorySavedByDedup,
                potentialSavings,
                top,
                topInstances,
                already,
                opps,
                List.<JvmStringFlag>of());
    }

    private static List<StringInstanceEntry> topNInstances(
            List<StringInstanceEntry> candidates, int topN) {
        return candidates.stream()
                .sorted(Comparator.comparingLong(StringInstanceEntry::retainedSize).reversed())
                .limit(topN)
                .toList();
    }

    private static List<StringDeduplicationEntry> topNByPhysicalSavings(
            List<PhysicalSharingRow> rows, int topN, HeapView view) throws SQLException {
        List<PhysicalSharingRow> top = rows.stream()
                .filter(r -> r.refCount > 1)
                .sorted(Comparator.<PhysicalSharingRow>comparingLong(
                        r -> (r.refCount - 1) * r.arrayShallowSize).reversed())
                .limit(topN)
                .toList();
        List<StringDeduplicationEntry> out = new ArrayList<>(top.size());
        for (PhysicalSharingRow r : top) {
            String preview = previewFor(view, r.sampleStringId);
            out.add(new StringDeduplicationEntry(
                    preview,
                    toBoundedInt(r.refCount),
                    r.arrayShallowSize,
                    (r.refCount - 1) * r.arrayShallowSize));
        }
        return out;
    }

    private static List<StringDeduplicationEntry> topNByContentSavings(
            Map<String, ContentSharingRow> contentRows, int topN) {
        return contentRows.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, ContentSharingRow>>comparingLong(
                                e -> (e.getValue().distinctArrays - 1) * e.getValue().arrayShallowSize)
                        .reversed())
                .limit(topN)
                .map(e -> new StringDeduplicationEntry(
                        truncate(e.getKey()),
                        toBoundedInt(e.getValue().stringCount),
                        e.getValue().arrayShallowSize,
                        (e.getValue().distinctArrays - 1) * e.getValue().arrayShallowSize))
                .toList();
    }

    private static List<StringTopEntry> topNByRetained(
            Map<String, TopRetainedRow> topRows, int topN) {
        return topRows.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, TopRetainedRow>>comparingLong(
                                e -> e.getValue().retainedSize())
                        .reversed())
                .limit(topN)
                .map(e -> new StringTopEntry(
                        truncate(e.getKey()),
                        toBoundedInt(e.getValue().stringCount),
                        e.getValue().arrayShallowSize,
                        e.getValue().retainedSize()))
                .toList();
    }

    private static String previewFor(HeapView view, long sampleStringId) throws SQLException {
        Optional<String> content = view.findStringContent(sampleStringId);
        return content.map(StringAnalyzer::truncate).orElse("");
    }

    /**
     * Reads the leading {@link #PREVIEW_MAX_CHARS} characters of an over-cap
     * String's backing array and returns the truncated preview. Uses
     * {@link JavaStringDecoder#decodePreview} so only a bounded prefix is
     * pulled off the mmap; pathological multi-megabyte arrays cost ~200 B of
     * allocation here, not their full length. Returns empty when decoding
     * fails.
     */
    private static String decodeOverCapPreview(HeapView view, long stringId) {
        try {
            return JavaStringDecoder.decodePreview(view, stringId, PREVIEW_MAX_CHARS)
                    .map(d -> truncate(d.content()))
                    .orElse("");
        } catch (SQLException e) {
            return "";
        }
    }

    /**
     * Looks up the field id (= position in the inherited-chain field block,
     * which is what {@code outbound_ref.field_id} stores) of {@code value} on
     * the given String class. Returns {@code -1} if not found.
     */
    private static int findValueFieldId(HeapView view, long stringClassId) throws SQLException {
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(stringClassId);
        for (int i = 0; i < chain.size(); i++) {
            if (JdkFieldNames.STRING_VALUE.equals(chain.get(i).name())) {
                return i;
            }
        }
        return -1;
    }

    private static int toBoundedInt(long v) {
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    private static String truncate(String s) {
        return StringTruncate.to(s, PREVIEW_MAX_CHARS);
    }

    private static StringAnalysisReport emptyReport() {
        return new StringAnalysisReport(
                0, 0, 0, 0, 0, 0, 0,
                List.<StringTopEntry>of(),
                List.<StringInstanceEntry>of(),
                List.<StringDeduplicationEntry>of(),
                List.<StringDeduplicationEntry>of(),
                List.<JvmStringFlag>of());
    }

    private record PhysicalSharingRow(long arrayShallowSize, long refCount, long sampleStringId) {
    }

    private record ContentSharingRow(
            long stringCount, long distinctArrays, long arrayShallowSize, long sampleStringId) {

        ContentSharingRow merge(ContentSharingRow other) {
            return new ContentSharingRow(
                    stringCount + other.stringCount,
                    distinctArrays + other.distinctArrays,
                    Math.max(arrayShallowSize, other.arrayShallowSize),
                    Math.min(sampleStringId, other.sampleStringId));
        }
    }

    private record TopRetainedRow(
            long stringCount, long sumStringShallow, long distinctArrays, long arrayShallowSize) {

        long retainedSize() {
            return sumStringShallow + distinctArrays * arrayShallowSize;
        }

        TopRetainedRow merge(TopRetainedRow other) {
            return new TopRetainedRow(
                    stringCount + other.stringCount,
                    sumStringShallow + other.sumStringShallow,
                    distinctArrays + other.distinctArrays,
                    Math.max(arrayShallowSize, other.arrayShallowSize));
        }
    }
}
