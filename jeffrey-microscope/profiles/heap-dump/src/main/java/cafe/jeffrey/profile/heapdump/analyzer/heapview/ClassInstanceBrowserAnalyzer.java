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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.InstanceSortBy;
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
    private static final int MAX_SUPERCLASS_WALK = 20;

    /**
     * Class names for which we surface a per-instance referrer hint (which class
     * holds this specific instance) — currently only {@code byte[]}, whose bytes
     * are opaque without context.
     */
    private static final String BYTE_ARRAY = byte[].class.getSimpleName();

    private static final String STRING_CLASS = String.class.getName();

    private static final String SQL_BY_OBJECT_ID =
            "SELECT instance_id, shallow_size FROM instance "
                    + "WHERE class_id = ? ORDER BY instance_id LIMIT ? OFFSET ?";

    private static final String SQL_BY_RETAINED_SIZE =
            "SELECT i.instance_id, i.shallow_size, r.bytes "
                    + "FROM instance i "
                    + "LEFT JOIN retained_size r ON i.instance_id = r.instance_id "
                    + "WHERE i.class_id = ? "
                    + "ORDER BY r.bytes DESC NULLS LAST, i.instance_id "
                    + "LIMIT ? OFFSET ?";

    private ClassInstanceBrowserAnalyzer() {
    }

    public static ClassInstancesResponse browse(HeapView view, long classId) throws SQLException {
        return browse(view, classId, 0, DEFAULT_LIMIT, InstanceSortBy.OBJECT_ID);
    }

    public static ClassInstancesResponse browse(HeapView view, long classId, int offset, int limit)
            throws SQLException {
        return browse(view, classId, offset, limit, InstanceSortBy.OBJECT_ID);
    }

    public static ClassInstancesResponse browse(
            HeapView view, long classId, int offset, int limit, InstanceSortBy sortBy) throws SQLException {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative: offset=" + offset);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }
        if (sortBy == InstanceSortBy.RETAINED_SIZE && !view.hasDominatorTree()) {
            throw new IllegalStateException("Retained-size sort requires the dominator tree to be built");
        }

        JavaClassRow cls = view.findClassById(classId).orElse(null);
        String className = cls != null ? cls.name() : "<unknown class:0x" + Long.toHexString(classId) + ">";
        long totalLong = view.instanceCount(classId);
        int total = (int) Math.min(totalLong, Integer.MAX_VALUE);
        boolean haveDom = view.hasDominatorTree();
        boolean joinRetained = sortBy == InstanceSortBy.RETAINED_SIZE;
        boolean isEnum = cls != null && isEnumClass(view, cls);

        boolean isByteArray = BYTE_ARRAY.equals(className);

        record Row(long instanceId, int shallow, Long retained, Map<String, String> params) {
        }
        List<Row> rawRows = new ArrayList<>();
        String sql = joinRetained ? SQL_BY_RETAINED_SIZE : SQL_BY_OBJECT_ID;
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setLong(1, classId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long instanceId = rs.getLong(1);
                    int shallow = rs.getInt(2);
                    Map<String, String> params = readFieldsAsParams(view, instanceId);
                    Long retained;
                    if (joinRetained) {
                        long bytes = rs.getLong(3);
                        retained = rs.wasNull() ? null : bytes;
                    } else {
                        retained = haveDom ? view.findRetainedSize(instanceId).orElse(null) : null;
                    }
                    rawRows.add(new Row(instanceId, shallow, retained, params));
                }
            }
        }

        // byte[] rows are enriched in a second pass: batch-fetch one dominant referrer
        // per page-of-instances, and when that referrer is a String, render its decoded
        // content as the preview. Other classes use the standard per-instance preview path.
        Map<Long, ByteArrayHint> byteArrayHints = isByteArray
                ? loadByteArrayHints(view, rawRows.stream().map(Row::instanceId).toList())
                : Map.of();

        List<ClassInstanceEntry> entries = new ArrayList<>(rawRows.size());
        for (Row row : rawRows) {
            String preview;
            String referrerClass;
            if (isByteArray) {
                ByteArrayHint hint = byteArrayHints.getOrDefault(row.instanceId(), ByteArrayHint.NONE);
                referrerClass = hint.referrerClass();
                preview = hint.preview();
            } else {
                referrerClass = null;
                preview = ContentPreviewRenderer.renderOrNull(view, className, row.instanceId(), isEnum);
            }
            entries.add(new ClassInstanceEntry(
                    row.instanceId(), row.shallow(), row.retained(), row.params(), preview, referrerClass));
        }

        boolean hasMore = (long) offset + entries.size() < totalLong;
        return new ClassInstancesResponse(className, total, List.copyOf(entries), hasMore);
    }

    /** Referrer-class hint + (optional) decoded preview for one byte[] instance. */
    private record ByteArrayHint(String referrerClass, String preview) {
        private static final ByteArrayHint NONE = new ByteArrayHint(null, null);
    }

    /**
     * Batch-resolve the dominant referrer class for each byte[] instance on the page.
     * When the dominant referrer is {@code java.lang.String}, render the String's
     * decoded content as the preview so the UI can show what the bytes spell out.
     */
    private static Map<Long, ByteArrayHint> loadByteArrayHints(HeapView view, List<Long> instanceIds)
            throws SQLException {
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(instanceIds.size(), "?"));
        // Pick the lowest-source_id referrer per byte[] (deterministic when an array has
        // multiple holders — the rare shared/interned case). Resolve its class name and
        // (for Strings) the String instance id so we can render its content.
        String sql = "WITH ranked AS ("
                + "  SELECT o.target_id, o.source_id, src_i.class_id,"
                + "         ROW_NUMBER() OVER (PARTITION BY o.target_id ORDER BY o.source_id) AS rn"
                + "  FROM outbound_ref o"
                + "  JOIN instance src_i ON src_i.instance_id = o.source_id"
                + "  WHERE o.target_id IN (" + placeholders + ")"
                + ") "
                + "SELECT r.target_id, r.source_id, c.name "
                + "FROM ranked r "
                + "LEFT JOIN class c ON c.class_id = r.class_id "
                + "WHERE r.rn = 1";

        Map<Long, ByteArrayHint> out = new HashMap<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            for (int i = 0; i < instanceIds.size(); i++) {
                stmt.setLong(i + 1, instanceIds.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long targetId = rs.getLong(1);
                    long sourceId = rs.getLong(2);
                    String className = rs.getString(3);
                    String preview = STRING_CLASS.equals(className)
                            ? ContentPreviewRenderer.renderOrNull(view, STRING_CLASS, sourceId, false)
                            : null;
                    out.put(targetId, new ByteArrayHint(className, preview));
                }
            }
        }
        return out;
    }

    /**
     * Walks the super-class chain looking for {@code java.lang.Enum}, so the renderer
     * can resolve the {@code name} field uniformly for every enum subclass without
     * needing a dedicated case per enum type.
     */
    private static boolean isEnumClass(HeapView view, JavaClassRow start) throws SQLException {
        JavaClassRow current = start;
        for (int i = 0; i < MAX_SUPERCLASS_WALK; i++) {
            if (Enum.class.getName().equals(current.name())) {
                return true;
            }
            Long superId = current.superClassId();
            if (superId == null || superId == 0L) {
                return false;
            }
            JavaClassRow parent = view.findClassById(superId).orElse(null);
            if (parent == null) {
                return false;
            }
            current = parent;
        }
        return false;
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
