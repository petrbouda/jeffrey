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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ClassInstanceBrowserAnalyzer}.
 *
 * Pages over a class's instances; for each, decodes the field values into
 * {@code objectParams} so the UI's "Show me 100 instances of HashMap" view
 * has something to render.
 *
 * Field values are stringified using {@link #stringify}: OBJECT references
 * are rendered as {@code 0x<hex>}, primitives via {@link Object#toString}.
 *
 * If no .hprof is attached, {@code objectParams} is empty (instance ids and
 * sizes still come back).
 */
public final class ClassInstanceBrowserAnalyzer {

    private static final int DEFAULT_LIMIT = 100;

    private ClassInstanceBrowserAnalyzer() {
    }

    public static ClassInstancesResponse browse(HeapView view, long classId) throws SQLException {
        return browse(view, classId, 0, DEFAULT_LIMIT);
    }

    public static ClassInstancesResponse browse(HeapView view, long classId, int offset, int limit)
            throws SQLException {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative: offset=" + offset);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }

        JavaClassRow cls = view.findClassById(classId).orElse(null);
        String className = cls != null ? cls.name() : "<unknown class:0x" + Long.toHexString(classId) + ">";
        long totalLong = view.instanceCount(classId);
        int total = (int) Math.min(totalLong, Integer.MAX_VALUE);
        boolean haveDom = view.hasDominatorTree();

        List<ClassInstanceEntry> entries = new ArrayList<>();
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT instance_id, shallow_size FROM instance "
                        + "WHERE class_id = ? ORDER BY instance_id LIMIT ? OFFSET ?")) {
            stmt.setLong(1, classId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long instanceId = rs.getLong(1);
                    int shallow = rs.getInt(2);
                    Map<String, String> params = readFieldsAsParams(view, instanceId);
                    Long retained = haveDom ? probeRetained(view, instanceId) : null;
                    entries.add(new ClassInstanceEntry(instanceId, shallow, retained, params));
                }
            }
        }

        boolean hasMore = (long) offset + entries.size() < totalLong;
        return new ClassInstancesResponse(className, total, List.copyOf(entries), hasMore);
    }

    private static Map<String, String> readFieldsAsParams(HeapView view, long instanceId) throws SQLException {
        try {
            List<InstanceFieldValue> fields = view.readInstanceFields(instanceId);
            Map<String, String> out = new LinkedHashMap<>(fields.size());
            for (InstanceFieldValue f : fields) {
                out.put(f.name(), stringify(f.value()));
            }
            return out;
        } catch (IllegalStateException noHprof) {
            return Map.of();
        }
    }

    private static Long probeRetained(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /**
     * Stringifies a decoded field value. OBJECT references render as a hex
     * id so a UI link can resolve them; primitives use {@code toString}.
     */
    private static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Long ref) {
            // Instance field decoder returns OBJECT as Long. 0 means null reference.
            return ref == 0L ? "null" : "0x" + Long.toHexString(ref);
        }
        if (value instanceof Character c) {
            return "'" + c + "'";
        }
        return value.toString();
    }
}
