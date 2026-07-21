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

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.DuplicateArrayGroup;
import cafe.jeffrey.profile.heapdump.model.DuplicateDataReport;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Finds byte-identical primitive arrays — the raw storage behind buffers,
 * caches, deserialized payloads and non-deduplicated strings — and reports how
 * much heap could be reclaimed by sharing a single copy per group.
 *
 * <p>Arrays are grouped by {@code (primitiveType, length, XXHash64(content))};
 * content is read straight from the mapped {@code .hprof} one array at a time,
 * so peak memory is a single array's payload. A 64-bit content hash stands in
 * for full byte equality — with equal type and length the collision
 * probability is negligible for reporting purposes.
 *
 * <p>Arrays smaller than {@link #MIN_CONTENT_BYTES} are ignored (empty/near-empty
 * arrays are dominated by legitimately shared constants), and arrays larger
 * than {@link #MAX_HASH_BYTES} are skipped rather than partially hashed, so a
 * prefix collision can never masquerade as a duplicate; the skipped count is
 * surfaced in the report.
 */
public final class DuplicateArrayAnalyzer {

    private static final int DEFAULT_TOP_N = 50;

    /** Arrays with payloads below this size are noise (shared empty arrays etc.). */
    private static final int MIN_CONTENT_BYTES = 16;

    /** Arrays larger than this are skipped instead of partially hashed. */
    private static final int MAX_HASH_BYTES = 64 * 1024 * 1024;

    private static final long HASH_SEED = 0x9E3779B97F4A7C15L;

    private static final int SAMPLE_IDS_PER_GROUP = 3;

    private static final int PREVIEW_BYTES = 256;

    private static final int PREVIEW_MAX_CHARS = 60;

    private static final String PRIMITIVE_ARRAYS_SQL = """
            SELECT i.instance_id, i.array_length, i.primitive_type, i.shallow_size, c.name
            FROM instance i
            LEFT JOIN class c ON c.class_id = i.class_id
            WHERE i.record_kind = 2
            """;

    private DuplicateArrayAnalyzer() {
    }

    public static DuplicateDataReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static DuplicateDataReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        XXHash64 hasher = XXHashFactory.fastestJavaInstance().hash64();
        Map<GroupKey, GroupAcc> groups = new HashMap<>();
        long totalArrays = 0;
        long totalArrayBytes = 0;
        long oversizedSkipped = 0;

        try (PreparedStatement stmt =
                     view.databaseClient().connection().prepareStatement(PRIMITIVE_ARRAYS_SQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                long instanceId = rs.getLong(1);
                int arrayLength = rs.getInt(2);
                byte primitiveType = rs.getByte(3);
                long shallowSize = rs.getLong(4);
                String typeName = rs.getString(5);

                totalArrays++;
                totalArrayBytes += shallowSize;

                int elementSize = HprofTag.BasicType.sizeOf(primitiveType);
                long contentBytes = (long) arrayLength * Math.max(elementSize, 1);
                if (contentBytes < MIN_CONTENT_BYTES) {
                    continue;
                }
                if (contentBytes > MAX_HASH_BYTES) {
                    oversizedSkipped++;
                    continue;
                }

                byte[] content = view.readPrimitiveArrayBytes(instanceId);
                if (content.length == 0) {
                    continue;
                }
                long hash = hasher.hash(content, 0, content.length, HASH_SEED);
                GroupKey key = new GroupKey(primitiveType, arrayLength, hash);
                GroupAcc acc = groups.computeIfAbsent(key, k -> new GroupAcc(typeName, shallowSize));
                acc.add(instanceId);
            }
        }

        long duplicateGroups = 0;
        long duplicateArrayCount = 0;
        long potentialSavings = 0;
        List<Map.Entry<GroupKey, GroupAcc>> duplicates = new ArrayList<>();
        for (Map.Entry<GroupKey, GroupAcc> e : groups.entrySet()) {
            GroupAcc acc = e.getValue();
            if (acc.count < 2) {
                continue;
            }
            duplicateGroups++;
            duplicateArrayCount += acc.count - 1;
            potentialSavings += acc.wastedBytes();
            duplicates.add(e);
        }
        duplicates.sort(Comparator.comparingLong(
                (Map.Entry<GroupKey, GroupAcc> e) -> e.getValue().wastedBytes()).reversed());

        List<DuplicateArrayGroup> topGroups = new ArrayList<>(Math.min(topN, duplicates.size()));
        for (Map.Entry<GroupKey, GroupAcc> e : duplicates.subList(0, Math.min(topN, duplicates.size()))) {
            GroupKey key = e.getKey();
            GroupAcc acc = e.getValue();
            String preview = renderPreview(view, acc.firstInstanceId, key.primitiveType());
            topGroups.add(new DuplicateArrayGroup(
                    acc.typeName == null ? "<unknown>" : acc.typeName,
                    key.arrayLength(),
                    acc.count,
                    acc.shallowSize,
                    acc.wastedBytes(),
                    preview,
                    acc.sampleIds()));
        }

        return new DuplicateDataReport(
                totalArrays,
                totalArrayBytes,
                duplicateGroups,
                duplicateArrayCount,
                potentialSavings,
                oversizedSkipped,
                topGroups);
    }

    /**
     * Human preview of the shared content: decoded text for byte/char arrays
     * that look textual, a hex prefix otherwise.
     */
    private static String renderPreview(HeapView view, long instanceId, byte primitiveType) {
        try {
            byte[] prefix = view.readPrimitiveArrayBytes(instanceId, PREVIEW_BYTES);
            if (prefix.length == 0) {
                return "";
            }
            if (primitiveType == HprofTag.BasicType.BYTE && isMostlyPrintableAscii(prefix)) {
                return truncate(new String(prefix, StandardCharsets.US_ASCII));
            }
            if (primitiveType == HprofTag.BasicType.CHAR) {
                return truncate(new String(prefix, StandardCharsets.UTF_16BE));
            }
            return truncate(toHex(prefix));
        } catch (SQLException | RuntimeException e) {
            return "";
        }
    }

    private static boolean isMostlyPrintableAscii(byte[] bytes) {
        int printable = 0;
        for (byte b : bytes) {
            if (b >= 0x20 && b < 0x7F) {
                printable++;
            }
        }
        return printable * 10 >= bytes.length * 9;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(Math.min(bytes.length, 16) * 3);
        for (int i = 0; i < bytes.length && i < 16; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private static String truncate(String value) {
        String sanitized = value.strip();
        if (sanitized.length() <= PREVIEW_MAX_CHARS) {
            return sanitized;
        }
        return sanitized.substring(0, PREVIEW_MAX_CHARS) + "…";
    }

    private record GroupKey(byte primitiveType, int arrayLength, long contentHash) {
    }

    private static final class GroupAcc {

        private final String typeName;
        private final long shallowSize;
        private final long[] samples = new long[SAMPLE_IDS_PER_GROUP];
        private long firstInstanceId;
        private int count;

        private GroupAcc(String typeName, long shallowSize) {
            this.typeName = typeName;
            this.shallowSize = shallowSize;
        }

        void add(long instanceId) {
            if (count == 0) {
                firstInstanceId = instanceId;
            }
            if (count < SAMPLE_IDS_PER_GROUP) {
                samples[count] = instanceId;
            }
            count++;
        }

        long wastedBytes() {
            return (long) (count - 1) * shallowSize;
        }

        List<Long> sampleIds() {
            List<Long> out = new ArrayList<>(Math.min(count, SAMPLE_IDS_PER_GROUP));
            for (int i = 0; i < count && i < SAMPLE_IDS_PER_GROUP; i++) {
                out.add(samples[i]);
            }
            return out;
        }
    }
}
