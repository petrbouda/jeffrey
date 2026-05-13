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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.parser.DumpMetadata;
import cafe.jeffrey.profile.heapdump.parser.GcRootRow;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HistogramRow;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;
import cafe.jeffrey.profile.heapdump.parser.OutboundRefRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;

/**
 * Minimal {@link HeapView} implementation backed by an externally provided
 * DuckDB connection — for integration tests. Implements just the surface the
 * OQL executor actually calls; everything else throws.
 */
final class TestHeapView implements HeapView {

    private final Connection connection;
    private final Map<Long, List<InstanceFieldValue>> cannedFields = new HashMap<>();
    private final Map<Long, byte[]> cannedPrimitiveArrays = new HashMap<>();
    private final Map<Long, String> cannedStrings = new HashMap<>();

    TestHeapView(Connection connection) {
        this.connection = connection;
    }

    /** Seed canned instance fields for an instance — used by path-expression tests. */
    void setFields(long instanceId, List<InstanceFieldValue> fields) {
        cannedFields.put(instanceId, fields);
    }

    /** Seed canned primitive-array bytes for an instance — used by string-decoder tests. */
    void setPrimitiveArrayBytes(long instanceId, byte[] bytes) {
        cannedPrimitiveArrays.put(instanceId, bytes);
    }

    /**
     * Convenience: seed a {@code java.lang.String} with the given content
     * decoded directly (bypasses the byte-coder dance, which the production
     * {@code JavaStringDecoder} re-creates from the index).
     */
    void setStringContent(long instanceId, String content) {
        cannedStrings.put(instanceId, content);
    }

    String cannedString(long instanceId) {
        return cannedStrings.get(instanceId);
    }

    @Override
    public Optional<String> findStringContent(long instanceId) {
        return Optional.ofNullable(cannedStrings.get(instanceId));
    }

    @Override
    public Connection connection() {
        return connection;
    }

    @Override
    public List<JavaClassRow> findClassesByName(String name) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT class_id, class_serial, name, is_array, super_class_id, classloader_id, "
                        + "signers_id, protection_domain_id, instance_size, static_fields_size, file_offset "
                        + "FROM class WHERE name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                List<JavaClassRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapClass(rs));
                }
                return rows;
            }
        }
    }

    @Override
    public Optional<JavaClassRow> findClassById(long classId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT class_id, class_serial, name, is_array, super_class_id, classloader_id, "
                        + "signers_id, protection_domain_id, instance_size, static_fields_size, file_offset "
                        + "FROM class WHERE class_id = ?")) {
            stmt.setLong(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapClass(rs)) : Optional.empty();
            }
        }
    }

    private static JavaClassRow mapClass(ResultSet rs) throws SQLException {
        long classId = rs.getLong(1);
        int classSerial = rs.getInt(2);
        String name = rs.getString(3);
        boolean isArray = rs.getBoolean(4);
        long superClassId = rs.getLong(5);
        Long superClassIdBoxed = rs.wasNull() ? null : superClassId;
        long classloaderId = rs.getLong(6);
        Long classloaderIdBoxed = rs.wasNull() ? null : classloaderId;
        long signersId = rs.getLong(7);
        Long signersIdBoxed = rs.wasNull() ? null : signersId;
        long protectionDomainId = rs.getLong(8);
        Long protectionDomainIdBoxed = rs.wasNull() ? null : protectionDomainId;
        int instanceSize = rs.getInt(9);
        int staticFieldsSize = rs.getInt(10);
        long fileOffset = rs.getLong(11);
        return new JavaClassRow(
                classId, classSerial, name, isArray, superClassIdBoxed,
                classloaderIdBoxed, signersIdBoxed, protectionDomainIdBoxed,
                instanceSize, staticFieldsSize, fileOffset);
    }

    @Override
    public void close() {
        // Connection lifecycle is owned by @DuckDBTest; nothing to do.
    }

    // ---- Everything else is out of scope for Phase 2 tests --------------

    @Override
    public DumpMetadata metadata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<JavaClassRow> classes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<InstanceRow> instances(long classId) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "SELECT instance_id, class_id, file_offset, record_kind, shallow_size, array_length, primitive_type "
                        + "FROM instance WHERE class_id = ? ORDER BY instance_id");
        stmt.setLong(1, classId);
        java.sql.ResultSet rs = stmt.executeQuery();
        java.util.Iterator<InstanceRow> it = new java.util.Iterator<>() {
            private boolean hasNext;
            private boolean checked;

            @Override
            public boolean hasNext() {
                if (!checked) {
                    try {
                        hasNext = rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    checked = true;
                }
                return hasNext;
            }

            @Override
            public InstanceRow next() {
                if (!checked) {
                    hasNext();
                }
                checked = false;
                try {
                    return mapInstance(rs);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return java.util.stream.StreamSupport
                .stream(java.util.Spliterators.spliteratorUnknownSize(it, java.util.Spliterator.ORDERED), false)
                .onClose(() -> {
                    try { rs.close(); } catch (SQLException ignored) {}
                    try { stmt.close(); } catch (SQLException ignored) {}
                });
    }

    @Override
    public Optional<InstanceRow> findInstanceById(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT instance_id, class_id, file_offset, record_kind, shallow_size, array_length, primitive_type "
                        + "FROM instance WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapInstance(rs)) : Optional.empty();
            }
        }
    }

    private static InstanceRow mapInstance(java.sql.ResultSet rs) throws SQLException {
        long instanceId = rs.getLong(1);
        long classIdVal = rs.getLong(2);
        Long classId = rs.wasNull() ? null : classIdVal;
        long fileOffset = rs.getLong(3);
        int kindOrdinal = rs.getInt(4);
        int shallowSize = rs.getInt(5);
        int arrayLengthVal = rs.getInt(6);
        Integer arrayLength = rs.wasNull() ? null : arrayLengthVal;
        int primitiveTypeVal = rs.getInt(7);
        Integer primitiveType = rs.wasNull() ? null : primitiveTypeVal;
        return new InstanceRow(instanceId, classId, fileOffset,
                InstanceRow.Kind.fromOrdinal(kindOrdinal), shallowSize, arrayLength, primitiveType);
    }

    @Override
    public long instanceCount(long classId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long totalInstanceCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long totalShallowSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long classCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GcRootRow> gcRoots() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGcRoot(long instanceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long gcRootCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<OutboundRefRow> outboundRefs(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT source_id, target_id, field_kind, field_id FROM outbound_ref WHERE source_id = ?")) {
            stmt.setLong(1, instanceId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                List<OutboundRefRow> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    rows.add(new OutboundRefRow(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)));
                }
                return rows;
            }
        }
    }

    @Override
    public List<OutboundRefRow> inboundRefs(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT source_id, target_id, field_kind, field_id FROM outbound_ref WHERE target_id = ?")) {
            stmt.setLong(1, instanceId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                List<OutboundRefRow> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    rows.add(new OutboundRefRow(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)));
                }
                return rows;
            }
        }
    }

    @Override
    public long outboundRefCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long dominatorOf(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT dominator_id FROM dominator WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    @Override
    public long retainedSize(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    public boolean hasDominatorTree() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InstanceFieldDescriptor> instanceFields(long classId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InstanceFieldDescriptor> instanceFieldsWithChain(long classId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InstanceFieldValue> readInstanceFields(long instanceId) {
        return cannedFields.getOrDefault(instanceId, List.of());
    }

    @Override
    public byte[] readPrimitiveArrayBytes(long instanceId) {
        return cannedPrimitiveArrays.getOrDefault(instanceId, new byte[0]);
    }

    @Override
    public byte[] readInstanceContentBytes(long instanceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] readInstanceContentBytes(InstanceRow row) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HeapView openReadOnlyCopy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> findString(long stringId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<HistogramRow> classHistogram() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int readInt(long fileOffset) {
        throw new UnsupportedOperationException();
    }
}
