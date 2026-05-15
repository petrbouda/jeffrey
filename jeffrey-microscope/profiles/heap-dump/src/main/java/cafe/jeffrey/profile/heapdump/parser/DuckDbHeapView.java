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
package cafe.jeffrey.profile.heapdump.parser;

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.shared.persistence.GroupLabel;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cafe.jeffrey.profile.heapdump.parser.JdbcNullable.nullableInt;
import static cafe.jeffrey.profile.heapdump.parser.JdbcNullable.nullableLong;

/**
 * DuckDB-backed implementation of {@link HeapView}.
 * <p>
 * Opens the index DB in {@code access_mode=read_only} so multiple views can
 * coexist and so an accidental UPDATE can't corrupt a built index.
 */
final class DuckDbHeapView implements HeapView {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbHeapView.class);

    private static final String CLASS_COLUMNS =
            "class_id, class_serial, name, is_array, super_class_id, classloader_id, signers_id, "
                    + "protection_domain_id, instance_size, static_fields_size, file_offset";

    private static final String INSTANCE_COLUMNS =
            "instance_id, class_id, file_offset, record_kind, shallow_size, array_length, primitive_type";

    private static final String GC_ROOT_COLUMNS =
            "instance_id, root_kind, thread_serial, frame_index, file_offset";

    // ---- SQL constants ---------------------------------------------------

    private static final String SELECT_DUMP_METADATA =
            "SELECT hprof_path, hprof_size_bytes, hprof_mtime_ms, id_size, hprof_version, "
                    + "timestamp_ms, bytes_parsed, record_count, warning_count, truncated, "
                    + "parser_version, parsed_at_ms, compressed_oops FROM dump_metadata";

    private static final String SELECT_ALL_CLASSES =
            "SELECT " + CLASS_COLUMNS + " FROM class ORDER BY class_id";

    private static final String SELECT_CLASS_BY_ID =
            "SELECT " + CLASS_COLUMNS + " FROM class WHERE class_id = ?";

    private static final String SELECT_CLASSES_BY_NAME =
            "SELECT " + CLASS_COLUMNS + " FROM class WHERE name = ? ORDER BY class_id";

    private static final String SELECT_INSTANCES_BY_CLASS_ORDERED =
            "SELECT " + INSTANCE_COLUMNS + " FROM instance WHERE class_id = ? ORDER BY instance_id";

    private static final String SELECT_INSTANCE_BY_ID =
            "SELECT " + INSTANCE_COLUMNS + " FROM instance WHERE instance_id = ?";

    private static final String COUNT_INSTANCES_BY_CLASS =
            "SELECT COUNT(*) FROM instance WHERE class_id = ?";

    private static final String COUNT_INSTANCES =
            "SELECT COUNT(*) FROM instance";

    private static final String SUM_SHALLOW_SIZE =
            "SELECT COALESCE(SUM(shallow_size), 0) FROM instance";

    private static final String COUNT_CLASSES =
            "SELECT COUNT(*) FROM class";

    private static final String SELECT_GC_ROOTS_ORDERED =
            "SELECT " + GC_ROOT_COLUMNS + " FROM gc_root ORDER BY instance_id, root_kind";

    private static final String EXISTS_GC_ROOT =
            "SELECT 1 FROM gc_root WHERE instance_id = ? LIMIT 1";

    private static final String COUNT_GC_ROOTS =
            "SELECT COUNT(*) FROM gc_root";

    private static final String SELECT_OUTBOUND_REFS_BY_SOURCE =
            "SELECT source_id, target_id, field_kind, field_id "
                    + "FROM outbound_ref WHERE source_id = ? ORDER BY field_kind, field_id";

    private static final String SELECT_OUTBOUND_REFS_BY_TARGET =
            "SELECT source_id, target_id, field_kind, field_id "
                    + "FROM outbound_ref WHERE target_id = ? ORDER BY source_id, field_id";

    private static final String COUNT_OUTBOUND_REFS =
            "SELECT COUNT(*) FROM outbound_ref";

    private static final String SELECT_DOMINATOR_OF =
            "SELECT dominator_id FROM dominator WHERE instance_id = ?";

    private static final String SELECT_RETAINED_SIZE =
            "SELECT bytes FROM retained_size WHERE instance_id = ?";

    private static final String COUNT_DOMINATORS =
            "SELECT COUNT(*) FROM dominator";

    private static final String SELECT_INSTANCE_FIELDS_BY_CLASS =
            "SELECT class_id, field_index, name, basic_type "
                    + "FROM class_instance_field WHERE class_id = ? ORDER BY field_index";

    private static final String SELECT_STRING_BY_ID =
            "SELECT value FROM string WHERE string_id = ?";

    private static final String SELECT_STRING_CONTENT_BY_INSTANCE =
            "SELECT content FROM string_content WHERE instance_id = ?";

    // LEFT JOIN so primitive arrays (class_id NULL) and instances pointing to
    // missing class rows still appear in the histogram with className=NULL.
    private static final String SELECT_CLASS_HISTOGRAM =
            "SELECT i.class_id, c.name, COUNT(*) AS cnt, SUM(i.shallow_size) AS total "
                    + "FROM instance i LEFT JOIN class c ON i.class_id = c.class_id "
                    + "GROUP BY i.class_id, c.name "
                    + "ORDER BY total DESC, cnt DESC";

    private final Path path;
    private final Connection connection;
    private final HeapDumpDatabaseClient databaseClient;
    private final HprofMappedFile hprof;
    /**
     * Per-session cache for {@link #findStringContent(long)}. Heap-dump analyses
     * resolve the same Strings repeatedly (thread names, classloader names,
     * top-N labels, UI previews), and every cache miss is a DuckDB PK round-trip
     * (~50 µs). Lifetime matches this view's open session; dropped when
     * {@link #close()} runs. {@link Optional} captures the "no row" / "content
     * IS NULL" outcomes so the cache covers misses too.
     */
    private final ConcurrentMap<Long, Optional<String>> stringContentCache = new ConcurrentHashMap<>();

    private DuckDbHeapView(Path path, Connection connection, HprofMappedFile hprof) throws SQLException {
        this.path = path;
        this.connection = connection;
        this.databaseClient = new HeapDumpDatabaseClient(
                connection.unwrap(DuckDBConnection.class), GroupLabel.HEAP_DUMP_VIEW);
        this.hprof = hprof;
    }

    static DuckDbHeapView open(Path indexDbPath, HprofMappedFile hprof) throws SQLException, IOException {
        if (indexDbPath == null) {
            throw new IllegalArgumentException("indexDbPath must not be null");
        }
        if (!Files.exists(indexDbPath)) {
            throw new IOException("Heap dump index file does not exist: path=" + indexDbPath);
        }
        // DuckDB JDBC takes the URL after jdbc:duckdb: as a literal path; URL query
        // parameters are NOT parsed. Read-only mode is set via the Properties bag.
        String url = "jdbc:duckdb:" + indexDbPath.toAbsolutePath();
        Properties props = new Properties();
        props.setProperty("duckdb.read_only", "true");
        Connection conn = DriverManager.getConnection(url, props);
        LOG.debug("Opened heap dump index for reading: path={} hprof_attached={}",
                indexDbPath, hprof != null);
        return new DuckDbHeapView(indexDbPath, conn, hprof);
    }

    @Override
    public DumpMetadata metadata() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_DUMP_METADATA)) {
            if (!rs.next()) {
                throw new SQLException("dump_metadata table is empty: path=" + path);
            }
            return new DumpMetadata(
                    rs.getString(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    rs.getInt(4),
                    rs.getString(5),
                    rs.getLong(6),
                    rs.getLong(7),
                    rs.getLong(8),
                    rs.getLong(9),
                    rs.getBoolean(10),
                    rs.getString(11),
                    rs.getLong(12),
                    rs.getBoolean(13));
        }
    }

    // ---- Classes ---------------------------------------------------------

    @Override
    public List<JavaClassRow> classes() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL_CLASSES)) {
            List<JavaClassRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapClass(rs));
            }
            return rows;
        }
    }

    @Override
    public Optional<JavaClassRow> findClassById(long classId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_CLASS_BY_ID)) {
            stmt.setLong(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapClass(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<JavaClassRow> findClassesByName(String name) throws SQLException {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_CLASSES_BY_NAME)) {
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

    private static JavaClassRow mapClass(ResultSet rs) throws SQLException {
        return new JavaClassRow(
                rs.getLong(1),
                rs.getInt(2),
                rs.getString(3),
                rs.getBoolean(4),
                nullableLong(rs, 5),
                nullableLong(rs, 6),
                nullableLong(rs, 7),
                nullableLong(rs, 8),
                rs.getInt(9),
                rs.getInt(10),
                rs.getLong(11));
    }

    // ---- Instances -------------------------------------------------------

    @Override
    public Stream<InstanceRow> instances(long classId) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(SELECT_INSTANCES_BY_CLASS_ORDERED);
        stmt.setLong(1, classId);
        ResultSet rs = stmt.executeQuery();
        return resultSetStream(stmt, rs, DuckDbHeapView::mapInstance);
    }

    @Override
    public Optional<InstanceRow> findInstanceById(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_INSTANCE_BY_ID)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapInstance(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public long instanceCount(long classId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(COUNT_INSTANCES_BY_CLASS)) {
            stmt.setLong(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    public long totalInstanceCount() throws SQLException {
        return scalarLong(COUNT_INSTANCES);
    }

    @Override
    public long totalShallowSize() throws SQLException {
        return scalarLong(SUM_SHALLOW_SIZE);
    }

    @Override
    public long classCount() throws SQLException {
        return scalarLong(COUNT_CLASSES);
    }

    private long scalarLong(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static InstanceRow mapInstance(ResultSet rs) throws SQLException {
        return new InstanceRow(
                rs.getLong(1),
                nullableLong(rs, 2),
                rs.getLong(3),
                InstanceRow.Kind.fromOrdinal(rs.getInt(4)),
                rs.getInt(5),
                nullableInt(rs, 6),
                nullableInt(rs, 7));
    }

    // ---- GC roots --------------------------------------------------------

    @Override
    public List<GcRootRow> gcRoots() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_GC_ROOTS_ORDERED)) {
            List<GcRootRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapGcRoot(rs));
            }
            return rows;
        }
    }

    @Override
    public boolean isGcRoot(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(EXISTS_GC_ROOT)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public long gcRootCount() throws SQLException {
        return scalarLong(COUNT_GC_ROOTS);
    }

    // ---- Reference graph -------------------------------------------------

    @Override
    public List<OutboundRefRow> outboundRefs(long instanceId) throws SQLException {
        return queryRefs(SELECT_OUTBOUND_REFS_BY_SOURCE, instanceId);
    }

    @Override
    public List<OutboundRefRow> inboundRefs(long instanceId) throws SQLException {
        return queryRefs(SELECT_OUTBOUND_REFS_BY_TARGET, instanceId);
    }

    @Override
    public long outboundRefCount() throws SQLException {
        return scalarLong(COUNT_OUTBOUND_REFS);
    }

    @Override
    public long dominatorOf(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_DOMINATOR_OF)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    @Override
    public boolean hasDominatorTree() throws SQLException {
        return scalarLong(COUNT_DOMINATORS) > 0;
    }

    // ---- Class fields + instance values ----------------------------------

    @Override
    public List<InstanceFieldDescriptor> instanceFields(long classId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_INSTANCE_FIELDS_BY_CLASS)) {
            stmt.setLong(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<InstanceFieldDescriptor> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new InstanceFieldDescriptor(
                            rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getInt(4)));
                }
                return out;
            }
        }
    }

    @Override
    public List<InstanceFieldDescriptor> instanceFieldsWithChain(long classId) throws SQLException {
        List<InstanceFieldDescriptor> out = new ArrayList<>();
        long current = classId;
        while (current != 0L) {
            JavaClassRow cls = findClassById(current).orElse(null);
            if (cls == null) {
                break;
            }
            out.addAll(instanceFields(current));
            current = cls.superClassId() == null ? 0L : cls.superClassId();
        }
        return out;
    }

    @Override
    public int readInt(long fileOffset) {
        if (hprof == null) {
            throw new IllegalStateException(
                    "HeapView opened without a .hprof file; readInt is unavailable. "
                            + "Use HeapView.open(indexDb, hprof).");
        }
        return hprof.readInt(fileOffset);
    }

    @Override
    public List<InstanceFieldValue> readInstanceFields(long instanceId) throws SQLException {
        if (hprof == null) {
            throw new IllegalStateException(
                    "HeapView opened without a .hprof file; field-value reads are unavailable. "
                            + "Use HeapView.open(indexDb, hprof).");
        }
        InstanceRow inst = findInstanceById(instanceId).orElse(null);
        if (inst == null || inst.kind() != InstanceRow.Kind.INSTANCE || inst.classId() == null) {
            return List.of();
        }
        List<InstanceFieldDescriptor> chain = instanceFieldsWithChain(inst.classId());
        int idSize = hprof.header().idSize();
        long fieldsOffset = inst.fileOffset() + 2L * idSize + 8;
        long cursor = fieldsOffset;
        List<InstanceFieldValue> out = new ArrayList<>(chain.size());
        for (InstanceFieldDescriptor f : chain) {
            int sz = HprofTypeSize.sizeOf(f.basicType(), idSize);
            if (sz < 0) {
                break;
            }
            Object value = decodeBasicType(hprof, cursor, f.basicType(), idSize);
            out.add(new InstanceFieldValue(f.name(), f.basicType(), value));
            cursor += sz;
        }
        return out;
    }

    @Override
    public byte[] readPrimitiveArrayBytes(long instanceId) throws SQLException {
        return readPrimitiveArrayBytes(instanceId, Integer.MAX_VALUE);
    }

    @Override
    public byte[] readPrimitiveArrayBytes(long instanceId, int maxBytes) throws SQLException {
        if (hprof == null) {
            throw new IllegalStateException(
                    "HeapView opened without a .hprof file; primitive-array reads are unavailable. "
                            + "Use HeapView.open(indexDb, hprof).");
        }
        if (maxBytes <= 0) {
            return new byte[0];
        }
        InstanceRow inst = findInstanceById(instanceId).orElse(null);
        if (inst == null
                || inst.kind() != InstanceRow.Kind.PRIMITIVE_ARRAY
                || inst.arrayLength() == null
                || inst.primitiveType() == null) {
            return new byte[0];
        }
        int idSize = hprof.header().idSize();
        // PRIMITIVE_ARRAY_DUMP body layout: id + u4 + u4 + u1 + payload.
        long payloadOffset = inst.fileOffset() + idSize + 9L;
        int elementSize = HprofTypeSize.sizeOf(inst.primitiveType(), idSize);
        if (elementSize < 0) {
            return new byte[0];
        }
        long byteLengthLong = (long) inst.arrayLength() * elementSize;
        if (byteLengthLong > Integer.MAX_VALUE) {
            byteLengthLong = Integer.MAX_VALUE; // defensive cap
        }
        int byteLength = (int) Math.min(byteLengthLong, maxBytes);
        return hprof.readBytes(payloadOffset, byteLength);
    }

    @Override
    public byte[] readInstanceContentBytes(long instanceId) throws SQLException {
        if (hprof == null) {
            throw new IllegalStateException(
                    "HeapView opened without a .hprof file; instance content reads are unavailable. "
                            + "Use HeapView.open(indexDb, hprof).");
        }
        InstanceRow inst = findInstanceById(instanceId).orElse(null);
        if (inst == null) {
            return new byte[0];
        }
        return readInstanceContentBytes(inst);
    }

    @Override
    public byte[] readInstanceContentBytes(InstanceRow inst) {
        if (hprof == null) {
            throw new IllegalStateException(
                    "HeapView opened without a .hprof file; instance content reads are unavailable. "
                            + "Use HeapView.open(indexDb, hprof).");
        }
        if (inst == null) {
            return new byte[0];
        }
        int idSize = hprof.header().idSize();
        return switch (inst.kind()) {
            case INSTANCE -> {
                // Body: id + u4 + id + u4 numBytes + [field bytes]
                long fieldsOffset = inst.fileOffset() + 2L * idSize + 8;
                int len = readInstanceFieldsLength(inst, idSize);
                yield len <= 0 ? new byte[0] : hprof.readBytes(fieldsOffset, len);
            }
            case OBJECT_ARRAY -> {
                // Body: id + u4 + u4 length + id arrayClassId + [id * length]
                if (inst.arrayLength() == null) {
                    yield new byte[0];
                }
                long elementsOffset = inst.fileOffset() + 2L * idSize + 8;
                long byteLen = (long) inst.arrayLength() * idSize;
                yield byteLen > Integer.MAX_VALUE
                        ? new byte[0]
                        : hprof.readBytes(elementsOffset, (int) byteLen);
            }
            case PRIMITIVE_ARRAY -> readPrimitiveArrayBytesFor(inst, idSize);
        };
    }

    private byte[] readPrimitiveArrayBytesFor(InstanceRow inst, int idSize) {
        if (inst.kind() != InstanceRow.Kind.PRIMITIVE_ARRAY
                || inst.arrayLength() == null
                || inst.primitiveType() == null) {
            return new byte[0];
        }
        // PRIMITIVE_ARRAY_DUMP body layout: id + u4 + u4 + u1 + payload.
        long payloadOffset = inst.fileOffset() + idSize + 9L;
        int elementSize = HprofTypeSize.sizeOf(inst.primitiveType(), idSize);
        if (elementSize < 0) {
            return new byte[0];
        }
        long byteLengthLong = (long) inst.arrayLength() * elementSize;
        if (byteLengthLong > Integer.MAX_VALUE) {
            byteLengthLong = Integer.MAX_VALUE; // defensive cap
        }
        return hprof.readBytes(payloadOffset, (int) byteLengthLong);
    }

    @Override
    public HeapView openReadOnlyCopy() throws SQLException, IOException {
        // Mints a fresh DuckDB connection over the same index DB; shares the
        // mmap (Arena.ofShared, lock-free for concurrent reads).
        return open(path, hprof);
    }

    @Override
    public HeapDumpDatabaseClient databaseClient() {
        return databaseClient;
    }

    private int readInstanceFieldsLength(InstanceRow inst, int idSize) {
        // INSTANCE_DUMP body: id + u4 + id + u4(fieldsLength) + ... — read the u4 from .hprof.
        long u4Offset = inst.fileOffset() + 2L * idSize + 4;
        long len = Integer.toUnsignedLong(hprof.readInt(u4Offset));
        return len > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) len;
    }

    private static Object decodeBasicType(HprofMappedFile file, long offset, int type, int idSize) {
        return switch (type) {
            case HprofTag.BasicType.OBJECT -> file.readId(offset);
            case HprofTag.BasicType.BOOLEAN -> file.readByte(offset) != 0;
            case HprofTag.BasicType.BYTE -> file.readByte(offset);
            case HprofTag.BasicType.CHAR -> (char) file.readShort(offset);
            case HprofTag.BasicType.SHORT -> file.readShort(offset);
            case HprofTag.BasicType.INT -> file.readInt(offset);
            case HprofTag.BasicType.FLOAT -> Float.intBitsToFloat(file.readInt(offset));
            case HprofTag.BasicType.LONG -> file.readLong(offset);
            case HprofTag.BasicType.DOUBLE -> Double.longBitsToDouble(file.readLong(offset));
            default -> throw new IllegalArgumentException("Unknown basic type: type=" + type);
        };
    }

    private List<OutboundRefRow> queryRefs(String sql, long id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                List<OutboundRefRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OutboundRefRow(
                            rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)));
                }
                return rows;
            }
        }
    }

    private static GcRootRow mapGcRoot(ResultSet rs) throws SQLException {
        return new GcRootRow(
                rs.getLong(1),
                rs.getInt(2),
                nullableInt(rs, 3),
                nullableInt(rs, 4),
                rs.getLong(5));
    }

    // ---- Strings ---------------------------------------------------------

    @Override
    public Optional<String> findString(long stringId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_STRING_BY_ID)) {
            stmt.setLong(1, stringId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<String> findStringContent(long instanceId) throws SQLException {
        Optional<String> cached = stringContentCache.get(instanceId);
        if (cached != null) {
            return cached;
        }
        Optional<String> fresh = queryStringContent(instanceId);
        stringContentCache.put(instanceId, fresh);
        return fresh;
    }

    private Optional<String> queryStringContent(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_STRING_CONTENT_BY_INSTANCE)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String content = rs.getString(1);
                return rs.wasNull() ? Optional.empty() : Optional.of(content);
            }
        }
    }

    @Override
    public Optional<Long> findRetainedSize(long instanceId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_RETAINED_SIZE)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    // ---- Histogram -------------------------------------------------------

    @Override
    public List<HistogramRow> classHistogram() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_CLASS_HISTOGRAM)) {
            List<HistogramRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new HistogramRow(
                        nullableLong(rs, 1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getLong(4)));
            }
            return rows;
        }
    }

    // ---- Connection / lifecycle ------------------------------------------

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.warn("Failed to close heap-dump-index connection: path={} error={}", path, e.getMessage());
        }
    }

    // ---- Helpers ---------------------------------------------------------

    /**
     * Wraps a JDBC {@link ResultSet} as a closing {@link Stream}. The {@code stmt}
     * and {@code rs} are closed when the stream is closed (try-with-resources).
     */
    private static <T> Stream<T> resultSetStream(
            PreparedStatement stmt, ResultSet rs, RowMapper<T> mapper) {
        Iterator<T> it = new Iterator<>() {
            boolean fetched;
            boolean hasNext;

            @Override
            public boolean hasNext() {
                if (!fetched) {
                    try {
                        hasNext = rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    fetched = true;
                }
                return hasNext;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                fetched = false;
                try {
                    return mapper.map(rs);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Spliterator<T> sp = Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(sp, false).onClose(() -> {
            try {
                rs.close();
            } catch (SQLException ignored) {
            }
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        });
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
