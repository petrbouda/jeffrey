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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.DuplicateObjectEntry;
import cafe.jeffrey.profile.heapdump.model.DuplicateObjectsReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.DuplicateObjectAnalyzer}.
 *
 * Finds instances with byte-identical content within a class. Strategy:
 * <ol>
 *   <li>For each instance, hash its raw content bytes
 *       ({@link HeapView#readInstanceContentBytes}) using SHA-256 and key by
 *       (class_id, hash).</li>
 *   <li>Groups with more than one instance are duplicates; wasted bytes is
 *       (count - 1) × shallowSize.</li>
 *   <li>Top N returned ordered by total wasted bytes descending.</li>
 * </ol>
 *
 * Memory: O(distinct content hashes × 32 bytes) — fine for a few hundred
 * thousand classes; for huge heaps, rolling-hash + on-disk grouping would be
 * a follow-up.
 */
public final class DuplicateObjectAnalyzer {

    private static final int DEFAULT_TOP_N = 100;
    /** Skip tiny instances — their dedup wins are negligible and noise dominates. */
    private static final int MIN_SHALLOW_SIZE = 16;

    private DuplicateObjectAnalyzer() {
    }

    public static DuplicateObjectsReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static DuplicateObjectsReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        // Class id → name (resolved once).
        Map<Long, String> classNames = new HashMap<>();
        try (var stmt = view.connection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT class_id, name FROM class")) {
            while (rs.next()) {
                classNames.put(rs.getLong(1), rs.getString(2));
            }
        }

        Map<Group, Acc> groups = new HashMap<>();
        long totalAnalyzed = 0;

        // Walk every instance with a class_id; primitive arrays are skipped (same content
        // dedups via array-pool/string-table mechanisms, not generic dedup).
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT instance_id, class_id, shallow_size, record_kind FROM instance "
                        + "WHERE class_id IS NOT NULL AND shallow_size >= ?")) {
            stmt.setInt(1, MIN_SHALLOW_SIZE);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long instanceId = rs.getLong(1);
                    long classId = rs.getLong(2);
                    int shallowSize = rs.getInt(3);
                    int recordKind = rs.getInt(4);
                    if (recordKind == 2) { // PRIMITIVE_ARRAY: skip
                        continue;
                    }
                    byte[] content;
                    try {
                        content = view.readInstanceContentBytes(instanceId);
                    } catch (IllegalStateException noHprof) {
                        return new DuplicateObjectsReport(0, 0, List.of());
                    }
                    if (content.length == 0) {
                        continue;
                    }
                    Group key = new Group(classId, sha256(content));
                    groups.computeIfAbsent(key, k -> new Acc(shallowSize, contentPreview(content)))
                            .count++;
                    totalAnalyzed++;
                }
            }
        }

        long totalWasted = 0;
        List<DuplicateObjectEntry> entries = new ArrayList<>();
        for (Map.Entry<Group, Acc> e : groups.entrySet()) {
            int count = e.getValue().count;
            if (count <= 1) {
                continue;
            }
            int shallow = e.getValue().shallowSize;
            long wasted = (long) (count - 1) * shallow;
            totalWasted += wasted;
            String className = classNames.getOrDefault(e.getKey().classId(), "<unknown>");
            entries.add(new DuplicateObjectEntry(className, e.getValue().preview, count, shallow, wasted));
        }
        entries.sort(Comparator.comparingLong(DuplicateObjectEntry::totalWastedBytes).reversed());
        if (entries.size() > topN) {
            entries = entries.subList(0, topN);
        }

        return new DuplicateObjectsReport(totalAnalyzed, totalWasted, List.copyOf(entries));
    }

    private static String contentPreview(byte[] bytes) {
        // Hex preview of up to the first 16 bytes — useful for quick eyeballing.
        int n = Math.min(bytes.length, 16);
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
            if (i + 1 < n) {
                sb.append(' ');
            }
        }
        if (bytes.length > 16) {
            sb.append("…");
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    private record Group(long classId, byte[] hash) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Group g && g.classId == classId && java.util.Arrays.equals(g.hash, hash);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(classId) * 31 + java.util.Arrays.hashCode(hash);
        }
    }

    private static final class Acc {
        final int shallowSize;
        final String preview;
        int count;

        Acc(int shallowSize, String preview) {
            this.shallowSize = shallowSize;
            this.preview = preview;
        }
    }

}
