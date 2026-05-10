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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;

/**
 * Builds a DuckDB index ({@code .idx.duckdb}) for an HPROF heap dump.
 *
 * Two passes over the mmaped file:
 * <ol>
 *   <li>Top-level walk: STRING records stream into the {@code string} table;
 *       LOAD_CLASS entries and HEAP_DUMP_SEGMENT regions are buffered in memory.</li>
 *   <li>Region walk: CLASS_DUMP, INSTANCE_DUMP, OBJECT_ARRAY_DUMP, PRIMITIVE_ARRAY_DUMP
 *       and ROOT_* sub-records stream into {@code class}, {@code instance} and
 *       {@code gc_root}. Class names are resolved via the in-memory string pool
 *       collected during pass 1.</li>
 * </ol>
 * {@code dump_metadata} and {@code parse_warning} are written at the end.
 *
 * Memory cost is dominated by the transient string pool plus the LoadClass map;
 * both are released as soon as the build completes.
 */
public final class HprofIndex {

    private static final Logger LOG = LoggerFactory.getLogger(HprofIndex.class);

    /** Bumped when the on-disk schema or extraction semantics change. */
    public static final String PARSER_VERSION = "0.1.0";

    private static final byte RECORD_KIND_INSTANCE = 0;
    private static final byte RECORD_KIND_OBJECT_ARRAY = 1;
    private static final byte RECORD_KIND_PRIMITIVE_ARRAY = 2;

    /** Java object header overhead used to approximate JVM-side shallow size. */
    private static final int OBJECT_HEADER_BYTES = 16;
    private static final int ARRAY_HEADER_BYTES = 16;

    /** Result of an index-build run. Counts reflect rows actually written. */
    public record IndexResult(
            long stringCount,
            long classCount,
            long instanceCount,
            long gcRootCount,
            long outboundRefCount,
            long warningCount,
            boolean truncated,
            long bytesParsed,
            long recordCount,
            Duration buildTime) {
    }

    private HprofIndex() {
    }

    /**
     * Builds (or rebuilds) the index next to {@code file} at {@code indexDbPath}.
     * Any existing index file is deleted first.
     */
    public static IndexResult build(HprofMappedFile file, Path indexDbPath, Clock clock)
            throws IOException, SQLException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (indexDbPath == null) {
            throw new IllegalArgumentException("indexDbPath must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }

        Files.deleteIfExists(indexDbPath);
        Files.deleteIfExists(HeapDumpIndexPaths.indexWalFor(file.path()));

        Elapsed<IndexResult> elapsed = Measuring.s(() -> {
            try (HeapDumpIndexDb db = HeapDumpIndexDb.openAndInitialize(indexDbPath)) {
                return doBuild(file, db, clock);
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        });

        IndexResult r = elapsed.entity();
        IndexResult withTime = new IndexResult(
                r.stringCount(), r.classCount(), r.instanceCount(), r.gcRootCount(),
                r.outboundRefCount(), r.warningCount(), r.truncated(), r.bytesParsed(),
                r.recordCount(), elapsed.duration());
        LOG.debug("Heap dump index built: path={} indexPath={} duration_ms={} strings={} classes={} instances={} gc_roots={} outbound_refs={} warnings={}",
                file.path(), indexDbPath, elapsed.duration().toMillis(),
                withTime.stringCount(), withTime.classCount(), withTime.instanceCount(),
                withTime.gcRootCount(), withTime.outboundRefCount(), withTime.warningCount());
        return withTime;
    }

    private static IndexResult doBuild(HprofMappedFile file, HeapDumpIndexDb db, Clock clock)
            throws IOException, SQLException {
        DuckDBConnection conn = db.connection();
        int idSize = file.header().idSize();

        TopLevelData top = walkTopLevel(file, conn);
        Counters counters = walkRegions(file, conn, top, idSize);
        long refCount = walkRegionsForRefs(file, conn, counters.classDumpsById, idSize, counters);
        long warningCount = top.warnings.size() + counters.warnings.size();
        boolean truncated = anyError(top.warnings) || anyError(counters.warnings);

        writeDumpMetadata(conn, file, clock, top, counters, warningCount, truncated);
        writeParseWarnings(conn, top.warnings);
        writeParseWarnings(conn, counters.warnings);

        // Force a checkpoint so all WAL contents land in the main DB file.
        // Without this, opening the file in read-only mode (HeapView) fails because
        // read-only connections cannot replay an outstanding WAL.
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT");
        }

        return new IndexResult(
                top.stringCount,
                counters.classCount,
                counters.instanceCount,
                counters.gcRootCount,
                counters.outboundRefCount,
                warningCount,
                truncated,
                file.size(),
                top.recordCount + counters.subRecordCount,
                Duration.ZERO); // overwritten by build()
    }

    // ---- Pass 1: top-level -----------------------------------------------

    private static final class TopLevelData {
        final Map<Long, byte[]> stringPool = new HashMap<>();
        final Map<Long, HprofRecord.LoadClass> loadClassByClassId = new HashMap<>();
        final List<HprofRecord.HeapDumpRegion> regions = new ArrayList<>();
        final List<ParseWarning> warnings = new ArrayList<>();
        long stringCount;
        long recordCount;
    }

    private static TopLevelData walkTopLevel(HprofMappedFile file, DuckDBConnection conn) throws SQLException {
        TopLevelData data = new TopLevelData();
        try (DuckDBAppender stringApp = conn.createAppender("string")) {
            HprofTopLevelReader.read(file, new HprofTopLevelReader.Listener() {
                @Override
                public void onRecord(HprofRecord.Top record) {
                    data.recordCount++;
                    switch (record) {
                        case HprofRecord.HprofString s -> {
                            try {
                                stringApp.beginRow();
                                stringApp.append(s.stringId());
                                stringApp.append(new String(s.utf8(), StandardCharsets.UTF_8));
                                stringApp.endRow();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            data.stringPool.put(s.stringId(), s.utf8());
                            data.stringCount++;
                        }
                        case HprofRecord.LoadClass lc -> data.loadClassByClassId.put(lc.classId(), lc);
                        case HprofRecord.HeapDumpRegion hdr -> data.regions.add(hdr);
                        case HprofRecord.OpaqueTop ignored -> {
                        }
                    }
                }

                @Override
                public void onWarning(ParseWarning warning) {
                    data.warnings.add(warning);
                }
            });
        }
        return data;
    }

    // ---- Pass 2: heap dump regions ---------------------------------------

    private static final class Counters {
        final List<ParseWarning> warnings = new ArrayList<>();
        /** classId → ClassDump, populated during the first region walk and consumed during the second. */
        final Map<Long, HprofRecord.ClassDump> classDumpsById = new HashMap<>();
        long classCount;
        long instanceCount;
        long gcRootCount;
        long subRecordCount;
        long outboundRefCount;
    }

    private static Counters walkRegions(
            HprofMappedFile file, DuckDBConnection conn, TopLevelData top, int idSize) throws SQLException {
        Counters c = new Counters();
        Set<Long> writtenClassIds = new HashSet<>();

        try (DuckDBAppender classApp = conn.createAppender("class");
             DuckDBAppender instApp = conn.createAppender("instance");
             DuckDBAppender rootApp = conn.createAppender("gc_root")) {

            for (HprofRecord.HeapDumpRegion region : top.regions) {
                HprofSubRecordReader.read(file, region.fileOffset(), region.byteLength(),
                        new HprofSubRecordReader.Listener() {
                            @Override
                            public void onRecord(HprofRecord.Sub sub) {
                                c.subRecordCount++;
                                try {
                                    dispatch(sub);
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            private void dispatch(HprofRecord.Sub sub) throws SQLException {
                                switch (sub) {
                                    case HprofRecord.ClassDump cd -> {
                                        if (writtenClassIds.add(cd.classId())) {
                                            appendClass(classApp, cd, top);
                                            c.classCount++;
                                            c.classDumpsById.put(cd.classId(), cd);
                                        }
                                    }
                                    case HprofRecord.InstanceDump id ->
                                            appendInstanceFromInstanceDump(instApp, id, idSize, c);
                                    case HprofRecord.ObjectArrayDump oa ->
                                            appendInstanceFromObjectArray(instApp, oa, idSize, c);
                                    case HprofRecord.PrimitiveArrayDump pa ->
                                            appendInstanceFromPrimitiveArray(instApp, pa, idSize, c);
                                    case HprofRecord.GcRoot root -> {
                                        appendGcRoot(rootApp, root);
                                        c.gcRootCount++;
                                    }
                                    case HprofRecord.OpaqueSub ignored -> {
                                    }
                                }
                            }

                            @Override
                            public void onWarning(ParseWarning warning) {
                                c.warnings.add(warning);
                            }
                        });
            }
        }

        return c;
    }

    /**
     * Second pass over the heap-dump regions, populating outbound_ref by decoding
     * INSTANCE_DUMP field bytes against each class's instance-field type list and
     * by walking OBJECT_ARRAY_DUMP element ids. Only emits refs whose target id is
     * non-zero (HPROF id 0 means "no reference").
     */
    private static long walkRegionsForRefs(
            HprofMappedFile file, DuckDBConnection conn,
            Map<Long, HprofRecord.ClassDump> classes, int idSize, Counters c) throws SQLException {
        long[] count = {0L};
        try (DuckDBAppender refApp = conn.createAppender("outbound_ref")) {
            // Re-walk top-level to find regions; cheap on the mmaped file.
            HprofTopLevelReader.read(file, new HprofTopLevelReader.Listener() {
                @Override
                public void onRecord(HprofRecord.Top record) {
                    if (record instanceof HprofRecord.HeapDumpRegion region) {
                        HprofSubRecordReader.read(file, region.fileOffset(), region.byteLength(),
                                new HprofSubRecordReader.Listener() {
                                    @Override
                                    public void onRecord(HprofRecord.Sub sub) {
                                        try {
                                            switch (sub) {
                                                case HprofRecord.InstanceDump id ->
                                                        count[0] += emitInstanceRefs(file, id, classes, idSize, refApp);
                                                case HprofRecord.ObjectArrayDump oa ->
                                                        count[0] += emitArrayRefs(file, oa, idSize, refApp);
                                                default -> {
                                                }
                                            }
                                        } catch (SQLException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                    }
                }
            });
        }
        c.outboundRefCount = count[0];
        return count[0];
    }

    private static int emitInstanceRefs(
            HprofMappedFile file, HprofRecord.InstanceDump inst,
            Map<Long, HprofRecord.ClassDump> classes, int idSize, DuckDBAppender refApp) throws SQLException {
        long fieldsOffset = inst.fileOffset() + 2L * idSize + 8;
        long cursor = fieldsOffset;
        long fieldsEnd = fieldsOffset + Integer.toUnsignedLong(inst.instanceFieldsByteLength());

        int globalIndex = 0;
        int emitted = 0;
        long currentClassId = inst.classId();
        // HPROF instance fields are laid out most-derived-first, walking the super-class chain.
        while (currentClassId != 0L) {
            HprofRecord.ClassDump cls = classes.get(currentClassId);
            if (cls == null) {
                break; // class not seen — give up gracefully on the rest of the chain
            }
            for (int type : cls.instanceFieldTypes()) {
                int sz = HprofTypeSize.sizeOf(type, idSize);
                if (sz < 0 || cursor + sz > fieldsEnd) {
                    return emitted; // defensive bail-out on bad data
                }
                if (type == HprofTag.BasicType.OBJECT) {
                    long targetId = file.readId(cursor);
                    if (targetId != 0L) {
                        refApp.beginRow();
                        refApp.append(inst.instanceId());
                        refApp.append(targetId);
                        refApp.append((byte) 0); // field_kind: instance_field
                        refApp.append(globalIndex);
                        refApp.endRow();
                        emitted++;
                    }
                }
                cursor += sz;
                globalIndex++;
            }
            currentClassId = cls.superClassId();
        }
        return emitted;
    }

    private static int emitArrayRefs(
            HprofMappedFile file, HprofRecord.ObjectArrayDump oa, int idSize, DuckDBAppender refApp)
            throws SQLException {
        long elementsOffset = oa.fileOffset() + 2L * idSize + 8;
        int emitted = 0;
        for (int i = 0; i < oa.length(); i++) {
            long targetId = file.readId(elementsOffset + (long) i * idSize);
            if (targetId != 0L) {
                refApp.beginRow();
                refApp.append(oa.arrayId());
                refApp.append(targetId);
                refApp.append((byte) 1); // field_kind: array_element
                refApp.append(i);
                refApp.endRow();
                emitted++;
            }
        }
        return emitted;
    }

    private static void appendClass(
            DuckDBAppender app, HprofRecord.ClassDump cd, TopLevelData top) throws SQLException {
        HprofRecord.LoadClass lc = top.loadClassByClassId.get(cd.classId());
        String name;
        int classSerial;
        if (lc == null) {
            name = "<unresolved-class:0x" + Long.toHexString(cd.classId()) + ">";
            classSerial = 0;
        } else {
            byte[] nameBytes = top.stringPool.get(lc.nameStringId());
            name = nameBytes != null
                    ? new String(nameBytes, StandardCharsets.UTF_8)
                    : "<unresolved-name:0x" + Long.toHexString(lc.nameStringId()) + ">";
            classSerial = lc.classSerial();
        }

        app.beginRow();
        app.append(cd.classId());
        app.append(classSerial);
        app.append(name);
        appendNullableId(app, cd.superClassId());
        appendNullableId(app, cd.classloaderId());
        appendNullableId(app, cd.signersId());
        appendNullableId(app, cd.protectionDomainId());
        app.append(cd.instanceSize());
        // static_fields_size: not tracked separately yet; populate with 0 to satisfy NOT NULL.
        app.append(0);
        app.append(cd.fileOffset());
        app.endRow();
    }

    private static void appendInstanceFromInstanceDump(
            DuckDBAppender app, HprofRecord.InstanceDump id, int idSize, Counters c) throws SQLException {
        // Approximate JVM-side shallow size: object header + encoded fields.
        int shallow = OBJECT_HEADER_BYTES + id.instanceFieldsByteLength();
        app.beginRow();
        app.append(id.instanceId());
        appendNullableId(app, id.classId());
        app.append(id.fileOffset());
        app.append(RECORD_KIND_INSTANCE);
        app.append(shallow);
        app.appendNull(); // array_length
        app.appendNull(); // primitive_type
        app.endRow();
        c.instanceCount++;
    }

    private static void appendInstanceFromObjectArray(
            DuckDBAppender app, HprofRecord.ObjectArrayDump oa, int idSize, Counters c) throws SQLException {
        long shallowLong = (long) ARRAY_HEADER_BYTES + (long) oa.length() * idSize;
        int shallow = (int) Math.min(shallowLong, Integer.MAX_VALUE);
        app.beginRow();
        app.append(oa.arrayId());
        appendNullableId(app, oa.arrayClassId());
        app.append(oa.fileOffset());
        app.append(RECORD_KIND_OBJECT_ARRAY);
        app.append(shallow);
        app.append(oa.length());
        app.appendNull(); // primitive_type
        app.endRow();
        c.instanceCount++;
    }

    private static void appendInstanceFromPrimitiveArray(
            DuckDBAppender app, HprofRecord.PrimitiveArrayDump pa, int idSize, Counters c) throws SQLException {
        int elementSize = HprofTypeSize.sizeOf(pa.elementType(), idSize);
        if (elementSize < 0) elementSize = 1; // defensive fallback
        long shallowLong = (long) ARRAY_HEADER_BYTES + (long) pa.length() * elementSize;
        int shallow = (int) Math.min(shallowLong, Integer.MAX_VALUE);
        app.beginRow();
        app.append(pa.arrayId());
        // Primitive arrays don't have a class entry in the heap; class_id stays NULL.
        app.appendNull();
        app.append(pa.fileOffset());
        app.append(RECORD_KIND_PRIMITIVE_ARRAY);
        app.append(shallow);
        app.append(pa.length());
        app.append((byte) pa.elementType());
        app.endRow();
        c.instanceCount++;
    }

    private static void appendGcRoot(DuckDBAppender app, HprofRecord.GcRoot root) throws SQLException {
        app.beginRow();
        app.append(root.instanceId());
        app.append((byte) root.rootKind());
        appendNullableInt(app, root.threadSerial());
        appendNullableInt(app, root.frameIndex());
        app.append(root.fileOffset());
        app.endRow();
    }

    // ---- Phase 3: metadata + warnings ------------------------------------

    private static void writeDumpMetadata(
            DuckDBConnection conn, HprofMappedFile file, Clock clock,
            TopLevelData top, Counters counters, long warningCount, boolean truncated) throws SQLException, IOException {
        long mtimeMs = Files.getLastModifiedTime(file.path()).toMillis();
        try (DuckDBAppender app = conn.createAppender("dump_metadata")) {
            app.beginRow();
            app.append(file.path().toAbsolutePath().toString());
            app.append(file.size());
            app.append(mtimeMs);
            app.append(file.header().idSize());
            app.append(file.header().version());
            app.append(file.header().timestampMs());
            app.append(file.size()); // bytes_parsed: best-effort = file size
            app.append(top.recordCount + counters.subRecordCount);
            app.append(warningCount);
            app.append(truncated);
            app.append(PARSER_VERSION);
            app.append(clock.instant().toEpochMilli());
            app.endRow();
        }
    }

    private static void writeParseWarnings(DuckDBConnection conn, List<ParseWarning> warnings) throws SQLException {
        if (warnings.isEmpty()) {
            return;
        }
        try (DuckDBAppender app = conn.createAppender("parse_warning")) {
            for (ParseWarning w : warnings) {
                app.beginRow();
                app.append(w.fileOffset());
                appendNullableInt(app, w.recordKind() == null ? null : w.recordKind());
                app.append((byte) w.severity().ordinal());
                app.append(w.message());
                app.endRow();
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------

    /** HPROF id 0 means "no reference"; map to NULL in the index for clean SQL semantics. */
    private static void appendNullableId(DuckDBAppender app, long id) throws SQLException {
        if (id == 0L) {
            app.appendNull();
        } else {
            app.append(id);
        }
    }

    private static void appendNullableInt(DuckDBAppender app, Integer value) throws SQLException {
        if (value == null) {
            app.appendNull();
        } else {
            app.append(value.intValue());
        }
    }

    private static void appendNullableInt(DuckDBAppender app, int value) throws SQLException {
        // Sentinel -1 used by parser for "absent" thread/frame fields.
        if (value < 0) {
            app.appendNull();
        } else {
            app.append(value);
        }
    }

    private static boolean anyError(List<ParseWarning> warnings) {
        for (ParseWarning w : warnings) {
            if (w.severity() == ParseWarning.Severity.ERROR) {
                return true;
            }
        }
        return false;
    }
}
