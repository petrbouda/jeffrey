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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.JvmStringFlag;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.StringDeduplicationEntry;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * Bulk-SQL String analysis.
 *
 * <p>Iterates every {@code java.lang.String} instance via a single JOIN that
 * pulls each String together with its {@code value} primitive-array's row, then
 * reads the raw array bytes once per unique array (dedup'd via {@code byArray}).
 * The previous per-instance pattern issued ~4 JDBC round-trips per String,
 * which on heaps with millions of Strings was the dominant cost of init.
 *
 * <p>String content is keyed in {@link Map} entries by a tiny {@code BytesKey}
 * wrapper that hashes / equals on the raw {@code byte[]} content + primitive
 * type, so we don't allocate a full Java {@code String} for every instance —
 * decoding to a real {@code String} happens only for the top-N entries that
 * survive sorting for display.
 *
 * <p>Surfaces:
 * <ul>
 *   <li><strong>Already deduplicated</strong>: distinct value-arrays referenced
 *       by more than one String. Savings = (refCount - 1) × arrayShallow.</li>
 *   <li><strong>Opportunities</strong>: identical content stored in multiple
 *       distinct value-arrays. Potential savings =
 *       (distinctArrays - 1) × arrayShallow.</li>
 * </ul>
 *
 * <p>JVM-flag enrichment from JFR events stays the caller's job — this
 * analyzer returns an empty {@link JvmStringFlag} list.
 */
public final class StringAnalyzer {

    private static final int DEFAULT_TOP_N = 100;
    private static final String JAVA_LANG_STRING = "java.lang.String";

    /**
     * Bulk join: every {@code String} instance paired with its {@code value}
     * primitive-array row in a single result set.
     *
     * <p>Parameters: {@code 1 = field_id} of String.value within the inherited
     * field chain, {@code 2 = String's class_id}.
     */
    private static final String STRINGS_WITH_VALUES_SQL = """
            SELECT
                s.instance_id      AS string_id,
                s.shallow_size     AS string_shallow,
                arr.instance_id    AS value_array_id,
                arr.primitive_type AS value_array_prim,
                arr.shallow_size   AS value_array_shallow
            FROM instance s
            LEFT JOIN outbound_ref ref
                ON ref.source_id = s.instance_id AND ref.field_id = ?
            LEFT JOIN instance arr
                ON arr.instance_id = ref.target_id
            WHERE s.class_id = ?
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

        List<JavaClassRow> stringClasses = view.findClassesByName(JAVA_LANG_STRING);
        if (stringClasses.isEmpty()) {
            return emptyReport();
        }

        Map<Long, ArrayUsage> byArray = new HashMap<>();
        Map<BytesKey, ContentUsage> byContent = new HashMap<>();
        long totalStrings = 0;
        long totalShallowSize = 0;

        for (JavaClassRow stringClass : stringClasses) {
            int valueFieldId = findValueFieldId(view, stringClass.classId());
            if (valueFieldId < 0) {
                continue; // No String.value field for this class — skip.
            }
            try (PreparedStatement stmt = view.connection().prepareStatement(STRINGS_WITH_VALUES_SQL)) {
                stmt.setInt(1, valueFieldId);
                stmt.setLong(2, stringClass.classId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long valueArrayId = rs.getLong(3);
                        if (rs.wasNull() || valueArrayId == 0L) {
                            // Match the previous behaviour: Strings without a
                            // resolvable value-array don't contribute to totals.
                            continue;
                        }
                        int valueArrayPrim = rs.getInt(4);
                        if (rs.wasNull()) {
                            continue;
                        }
                        int stringShallow = rs.getInt(2);
                        long valueArrayShallow = rs.getInt(5);

                        totalStrings++;
                        totalShallowSize += stringShallow;

                        ArrayUsage au = byArray.get(valueArrayId);
                        if (au == null) {
                            byte[] bytes = view.readPrimitiveArrayBytes(valueArrayId);
                            BytesKey contentKey = new BytesKey(bytes, valueArrayPrim);
                            au = new ArrayUsage(bytes, valueArrayPrim, valueArrayShallow, contentKey);
                            byArray.put(valueArrayId, au);
                            byContent.computeIfAbsent(contentKey,
                                            k -> new ContentUsage(valueArrayShallow))
                                    .distinctArrays.add(valueArrayId);
                        }
                        au.refCount++;
                        byContent.get(au.contentKey).totalCount++;
                    }
                }
            }
        }

        long uniqueArrays = byArray.size();
        long sharedArrays = byArray.values().stream().filter(a -> a.refCount > 1).count();
        long totalSharedStrings = byArray.values().stream().filter(a -> a.refCount > 1)
                .mapToLong(a -> a.refCount).sum();
        long memorySavedByDedup = byArray.values().stream().filter(a -> a.refCount > 1)
                .mapToLong(a -> (long) (a.refCount - 1) * a.arrayShallowSize).sum();

        // Already-deduplicated: distinct value-arrays referenced by more than one String.
        List<StringDeduplicationEntry> already = byArray.values().stream()
                .filter(a -> a.refCount > 1)
                .sorted(Comparator.comparingLong(StringAnalyzer::dedupSavings).reversed())
                .limit(topN)
                .map(a -> new StringDeduplicationEntry(
                        truncate(decodePreview(a.bytes, a.primitiveType)),
                        a.refCount,
                        a.arrayShallowSize,
                        (long) (a.refCount - 1) * a.arrayShallowSize))
                .toList();

        // Opportunities: same content stored in multiple distinct arrays.
        List<StringDeduplicationEntry> opps = byContent.entrySet().stream()
                .filter(e -> e.getValue().distinctArrays.size() > 1)
                .sorted(Comparator.<Map.Entry<BytesKey, ContentUsage>>comparingLong(
                                e -> oppSavings(e.getValue())).reversed())
                .limit(topN)
                .map(e -> new StringDeduplicationEntry(
                        truncate(decodePreview(e.getKey().bytes, e.getKey().primitiveType)),
                        e.getValue().totalCount,
                        e.getValue().arrayShallowSize,
                        (long) (e.getValue().distinctArrays.size() - 1) * e.getValue().arrayShallowSize))
                .toList();

        long potentialSavings = byContent.values().stream()
                .filter(c -> c.distinctArrays.size() > 1)
                .mapToLong(c -> (long) (c.distinctArrays.size() - 1) * c.arrayShallowSize)
                .sum();

        return new StringAnalysisReport(
                totalStrings,
                totalShallowSize,
                uniqueArrays,
                sharedArrays,
                totalSharedStrings,
                memorySavedByDedup,
                potentialSavings,
                already,
                opps,
                List.<JvmStringFlag>of());
    }

    /**
     * Looks up the field id (= position in the inherited-chain field block,
     * which is what {@code outbound_ref.field_id} stores) of {@code value} on
     * the given String class. Returns {@code -1} if not found.
     */
    private static int findValueFieldId(HeapView view, long stringClassId) throws SQLException {
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(stringClassId);
        for (int i = 0; i < chain.size(); i++) {
            if ("value".equals(chain.get(i).name())) {
                return i;
            }
        }
        return -1;
    }

    private static long dedupSavings(ArrayUsage a) {
        return (long) (a.refCount - 1) * a.arrayShallowSize;
    }

    private static long oppSavings(ContentUsage c) {
        return (long) (c.distinctArrays.size() - 1) * c.arrayShallowSize;
    }

    /**
     * Best-effort decoding of value-array bytes for the top-N display.
     * The Java 9+ {@code coder} byte isn't joined into the bulk query so we
     * pick a sensible default (LATIN1 for byte[], UTF-16 BE for char[]).
     * The preview is truncated to 200 chars anyway, so a wrong charset on
     * non-LATIN1 byte[] content only affects display, not dedup keying.
     */
    private static String decodePreview(byte[] bytes, int primitiveType) {
        if (primitiveType == HprofTag.BasicType.BYTE) {
            return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        if (primitiveType == HprofTag.BasicType.CHAR) {
            int n = bytes.length / 2;
            char[] chars = new char[n];
            for (int i = 0; i < n; i++) {
                int hi = bytes[i * 2] & 0xFF;
                int lo = bytes[i * 2 + 1] & 0xFF;
                chars[i] = (char) ((hi << 8) | lo);
            }
            return new String(chars);
        }
        return "";
    }

    private static String truncate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    private static StringAnalysisReport emptyReport() {
        return new StringAnalysisReport(
                0, 0, 0, 0, 0, 0, 0,
                List.<StringDeduplicationEntry>of(),
                List.<StringDeduplicationEntry>of(),
                List.<JvmStringFlag>of());
    }

    /**
     * Hash/equality key over a value-array's raw bytes paired with its
     * primitive type. Two arrays are "same content" only if they have the
     * exact same bytes <em>and</em> the same primitive type (a char[] and a
     * byte[] with byte-identical content still can't share storage).
     */
    private static final class BytesKey {
        final byte[] bytes;
        final int primitiveType;
        final int hash;

        BytesKey(byte[] bytes, int primitiveType) {
            this.bytes = bytes;
            this.primitiveType = primitiveType;
            this.hash = 31 * Arrays.hashCode(bytes) + primitiveType;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BytesKey other
                    && primitiveType == other.primitiveType
                    && Arrays.equals(bytes, other.bytes);
        }
    }

    private static final class ArrayUsage {
        final byte[] bytes;
        final int primitiveType;
        final long arrayShallowSize;
        final BytesKey contentKey;
        int refCount;

        ArrayUsage(byte[] bytes, int primitiveType, long arrayShallowSize, BytesKey contentKey) {
            this.bytes = bytes;
            this.primitiveType = primitiveType;
            this.arrayShallowSize = arrayShallowSize;
            this.contentKey = contentKey;
        }
    }

    private static final class ContentUsage {
        final long arrayShallowSize;
        final Set<Long> distinctArrays = new HashSet<>();
        int totalCount;

        ContentUsage(long arrayShallowSize) {
            this.arrayShallowSize = arrayShallowSize;
        }
    }
}
