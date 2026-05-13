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
import java.sql.PreparedStatement;
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

    /**
     * Heap below this size on a 64-bit dump is assumed to use compressed oops.
     * The JVM disables compressed oops once {@code -Xmx} exceeds 32 GiB (the
     * compressed-pointer encoding tops out there), so total .hprof size is a
     * reasonable proxy when no JFR-side hint is available.
     */
    private static final long COMPRESSED_OOPS_HEAP_LIMIT = 32L * 1024 * 1024 * 1024;

    /**
     * Per-instance memory layout the parser assumes when computing shallow size.
     *
     * <ul>
     *   <li>{@code objectHeader} / {@code arrayHeader} — bytes occupied by the
     *       JVM-side header, before any payload.</li>
     *   <li>{@code idSize} — HPROF on-disk pointer width, always 4 (32-bit) or
     *       8 (64-bit). Drives how OBJECT fields and OBJECT-array elements are
     *       encoded in the .hprof file.</li>
     *   <li>{@code oopSize} — pointer width on the live JVM heap. Equals
     *       {@code idSize} on 32-bit and on 64-bit without compressed oops;
     *       4 bytes when compressed oops are enabled. References take this
     *       many bytes per slot regardless of the on-disk width.</li>
     *   <li>{@code objectAlignment} — every allocation is rounded up to this
     *       boundary (HotSpot {@code MinObjAlignment}, 8 by default).</li>
     * </ul>
     *
     * Three concrete layouts:
     * <ul>
     *   <li>32-bit JVM (idSize == 4): header 8/12, oopSize 4</li>
     *   <li>64-bit compressed oops: header 16/16, oopSize 4</li>
     *   <li>64-bit uncompressed oops: header 16/24, oopSize 8</li>
     * </ul>
     */
    private record InstanceLayout(
            int objectHeader,
            int arrayHeader,
            int idSize,
            int oopSize,
            int objectAlignment) {

        static InstanceLayout from(int idSize, boolean compressedOops) {
            if (idSize == 4) {
                return new InstanceLayout(8, 12, 4, 4, 8);
            }
            return compressedOops
                    ? new InstanceLayout(16, 16, 8, 4, 8)
                    : new InstanceLayout(16, 24, 8, 8, 8);
        }

        /** OOP encoding delta: bytes over-counted per reference when the on-disk
         *  pointer is wider than the on-heap one (only non-zero with compressed oops). */
        int oopOverheadDelta() {
            return idSize - oopSize;
        }

        long alignUp(long size) {
            int a = objectAlignment;
            return (size + a - 1) / a * a;
        }
    }

    /**
     * HPROF doesn't emit CLASS_DUMP records for primitive-array types — only the
     * BasicType byte on each PRIMITIVE_ARRAY_DUMP. We synthesize one class row
     * per primitive type so primitive arrays show up in the histogram and in
     * every other class-keyed view. Synthetic ids are deeply negative so they
     * cannot collide with real HPROF object ids (which are non-negative).
     */
    private static final long PRIM_ARRAY_CLASS_ID_BASE = -1_000_000_000L;

    private static long primArrayClassId(int elementType) {
        return PRIM_ARRAY_CLASS_ID_BASE - elementType;
    }

    private static String primArrayName(int elementType) {
        return switch (elementType) {
            case 4 -> "boolean[]";
            case 5 -> "char[]";
            case 6 -> "float[]";
            case 7 -> "double[]";
            case 8 -> "byte[]";
            case 9 -> "short[]";
            case 10 -> "int[]";
            case 11 -> "long[]";
            default -> null;
        };
    }

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
        // Compressed-oops inference: 64-bit + heap below the JVM's 32 GiB threshold.
        // Total .hprof size is a coarse but reliable proxy for max heap.
        boolean compressedOops = (idSize == 8) && (file.size() < COMPRESSED_OOPS_HEAP_LIMIT);
        InstanceLayout layout = InstanceLayout.from(idSize, compressedOops);

        TopLevelData top = walkTopLevel(file, conn);
        writeStackTraces(conn, top);
        Counters counters = walkRegions(file, conn, top, idSize, layout);
        long refCount = walkRegionsForRefs(file, conn, counters.classDumpsById, idSize, counters);
        long warningCount = top.warnings.size() + counters.warnings.size();
        boolean truncated = anyError(top.warnings) || anyError(counters.warnings);

        // Instance dumps are appended with the on-disk field-block size (idSize per
        // OOP, no alignment). Correct them now that the full class hierarchy is
        // known: subtract the compressed-oops over-count and round each instance
        // up to the JVM allocation boundary.
        applyInstanceShallowCorrection(conn, counters.classDumpsById, layout);

        writeDumpMetadata(conn, file, clock, top, counters, warningCount, truncated, compressedOops);
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
        // STACK_FRAME / STACK_TRACE records buffered here so string-id resolution
        // happens after the entire top-level walk has populated stringPool — the
        // HPROF spec doesn't strictly order STRING before STACK_FRAME records.
        final List<HprofRecord.StackFrame> stackFrames = new ArrayList<>();
        final List<HprofRecord.StackTrace> stackTraces = new ArrayList<>();
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
                        case HprofRecord.StackFrame sf -> data.stackFrames.add(sf);
                        case HprofRecord.StackTrace st -> data.stackTraces.add(st);
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
            HprofMappedFile file, DuckDBConnection conn, TopLevelData top, int idSize, InstanceLayout layout) throws SQLException {
        Counters c = new Counters();
        Set<Long> writtenClassIds = new HashSet<>();

        try (DuckDBAppender classApp = conn.createAppender("class");
             DuckDBAppender fieldApp = conn.createAppender("class_instance_field");
             DuckDBAppender instApp = conn.createAppender("instance");
             DuckDBAppender rootApp = conn.createAppender("gc_root")) {

            // Seed synthetic primitive-array class rows so PRIMITIVE_ARRAY_DUMP
            // instances can join to a real class name.
            c.classCount += appendSyntheticPrimitiveArrayClasses(classApp);

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
                                            appendInstanceFields(fieldApp, cd, top);
                                            c.classCount++;
                                            c.classDumpsById.put(cd.classId(), cd);
                                        }
                                    }
                                    case HprofRecord.InstanceDump id ->
                                            appendInstanceFromInstanceDump(instApp, id, layout, c);
                                    case HprofRecord.ObjectArrayDump oa ->
                                            appendInstanceFromObjectArray(instApp, oa, idSize, layout, c);
                                    case HprofRecord.PrimitiveArrayDump pa ->
                                            appendInstanceFromPrimitiveArray(instApp, pa, idSize, layout, c);
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
        String rawName;
        int classSerial;
        if (lc == null) {
            rawName = "<unresolved-class:0x" + Long.toHexString(cd.classId()) + ">";
            classSerial = 0;
        } else {
            byte[] nameBytes = top.stringPool.get(lc.nameStringId());
            rawName = nameBytes != null
                    ? new String(nameBytes, StandardCharsets.UTF_8)
                    : "<unresolved-name:0x" + Long.toHexString(lc.nameStringId()) + ">";
            classSerial = lc.classSerial();
        }

        // Detect array-class by the raw HPROF prefix '[' before normalisation
        // (the user-facing form ends in "[]" so we'd lose the cheap signal otherwise).
        boolean isArray = !rawName.isEmpty() && rawName.charAt(0) == '[';
        String name = ClassNameFormatter.userFacing(rawName);

        app.beginRow();
        app.append(cd.classId());
        app.append(classSerial);
        app.append(name);
        app.append(isArray);
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

    /**
     * Inserts eight synthetic class rows — one per HPROF primitive basic-type —
     * so that {@link HprofRecord.PrimitiveArrayDump} instances can be assigned
     * a real {@code class_id} and surface in every class-keyed view (histogram,
     * dominator tree, leak suspects, …) with their proper {@code byte[]} /
     * {@code int[]} / … names. Synthetic ids live in {@link #PRIM_ARRAY_CLASS_ID_BASE}'s
     * deeply-negative range and cannot collide with real HPROF object ids.
     */
    private static long appendSyntheticPrimitiveArrayClasses(DuckDBAppender app) throws SQLException {
        long inserted = 0;
        for (int elementType = 4; elementType <= 11; elementType++) {
            String name = primArrayName(elementType);
            if (name == null) {
                continue;
            }
            app.beginRow();
            app.append(primArrayClassId(elementType));
            app.append(0); // class_serial — synthetic, no HPROF serial
            app.append(name);
            app.append(true); // is_array
            app.appendNull(); // super_class_id
            app.appendNull(); // classloader_id (bootstrap)
            app.appendNull(); // signers_id
            app.appendNull(); // protection_domain_id
            app.append(0); // instance_size — variable; per-instance shallow_size is on the instance row
            app.append(0); // static_fields_size
            app.append(-1L); // file_offset — no HPROF backing record
            app.endRow();
            inserted++;
        }
        return inserted;
    }

    private static void appendInstanceFields(
            DuckDBAppender app, HprofRecord.ClassDump cd, TopLevelData top) throws SQLException {
        long[] nameIds = cd.instanceFieldNameIds();
        int[] types = cd.instanceFieldTypes();
        for (int i = 0; i < types.length; i++) {
            byte[] nameBytes = top.stringPool.get(nameIds[i]);
            String name = nameBytes != null
                    ? new String(nameBytes, StandardCharsets.UTF_8)
                    : "<unresolved-field-name:0x" + Long.toHexString(nameIds[i]) + ">";
            app.beginRow();
            app.append(cd.classId());
            app.append(i);
            app.append(name);
            app.append((byte) types[i]);
            app.endRow();
        }
    }

    private static void appendInstanceFromInstanceDump(
            DuckDBAppender app, HprofRecord.InstanceDump id, InstanceLayout layout, Counters c) throws SQLException {
        // Approximate JVM-side shallow size: object header + encoded fields.
        int shallow = layout.objectHeader() + id.instanceFieldsByteLength();
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
            DuckDBAppender app, HprofRecord.ObjectArrayDump oa, int idSize, InstanceLayout layout, Counters c) throws SQLException {
        // On-heap OOP width can differ from the .hprof on-disk idSize when the
        // JVM uses compressed oops. Use oopSize so the array's shallow size
        // reflects what the JVM actually allocated.
        long unaligned = (long) layout.arrayHeader() + (long) oa.length() * layout.oopSize();
        long shallowLong = layout.alignUp(unaligned);
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
            DuckDBAppender app, HprofRecord.PrimitiveArrayDump pa, int idSize, InstanceLayout layout, Counters c) throws SQLException {
        int elementSize = HprofTypeSize.sizeOf(pa.elementType(), idSize);
        if (elementSize < 0) {
            elementSize = 1; // defensive fallback
        }
        long unaligned = (long) layout.arrayHeader() + (long) pa.length() * elementSize;
        long shallowLong = layout.alignUp(unaligned);
        int shallow = (int) Math.min(shallowLong, Integer.MAX_VALUE);
        app.beginRow();
        app.append(pa.arrayId());
        // HPROF has no class_id for primitive arrays — assign the synthetic class
        // row seeded during walkRegions so the instance joins to a real class name.
        app.append(primArrayClassId(pa.elementType()));
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

    // ---- Phase 3: shallow-size correction --------------------------------

    /**
     * Brings instance shallow_size in line with the JVM's allocated size:
     *
     * <ol>
     *   <li>Subtracts compressed-oops over-count for INSTANCE rows. The HPROF
     *       file encodes every OBJECT field as {@code idSize} bytes (8 on a
     *       64-bit dump), but at runtime each compressed OOP occupies
     *       {@code oopSize} (4). For every class we count OBJECT fields along
     *       the full super-class chain and subtract
     *       {@code chainOopCount * (idSize - oopSize)} from each instance.</li>
     *   <li>Rounds shallow_size up to {@link InstanceLayout#objectAlignment()}
     *       for every row (instances and arrays alike) — the JVM aligns each
     *       allocation to {@code MinObjAlignment}, default 8.</li>
     * </ol>
     *
     * Both corrections collapse to no-ops when they're not needed:
     * uncompressed 64-bit / 32-bit heaps have {@code oopOverheadDelta() == 0},
     * and rows already aligned to the boundary are left untouched.
     */
    private static void applyInstanceShallowCorrection(
            DuckDBConnection conn,
            Map<Long, HprofRecord.ClassDump> classDumps,
            InstanceLayout layout) throws SQLException {

        int oopDelta = layout.oopOverheadDelta();
        int alignment = layout.objectAlignment();

        if (oopDelta > 0 && !classDumps.isEmpty()) {
            Map<Long, Integer> chainOopByClass = computeChainOopCounts(classDumps);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TEMP TABLE _class_chain_oop (class_id BIGINT, oop_count INTEGER)");
            }
            try (DuckDBAppender app = conn.createAppender("_class_chain_oop")) {
                for (Map.Entry<Long, Integer> e : chainOopByClass.entrySet()) {
                    if (e.getValue() == 0) {
                        continue; // skip zero rows to keep the table tight
                    }
                    app.beginRow();
                    app.append(e.getKey());
                    app.append(e.getValue());
                    app.endRow();
                }
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "UPDATE instance SET shallow_size = shallow_size - c.oop_count * ? "
                            + "FROM _class_chain_oop c "
                            + "WHERE instance.class_id = c.class_id "
                            + "  AND instance.record_kind = " + RECORD_KIND_INSTANCE)) {
                st.setInt(1, oopDelta);
                st.executeUpdate();
            }
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE _class_chain_oop");
            }
        }

        // Round every row up to objectAlignment. Arrays are already aligned by
        // appendInstance*, but instances were written without alignment and may
        // have just become unaligned again after the OOP-delta subtraction.
        // Use modular arithmetic (no division) so the math stays in INTEGER
        // domain — DuckDB's `/` promotes to floating point on bound parameters.
        try (PreparedStatement st = conn.prepareStatement(
                "UPDATE instance SET shallow_size = shallow_size + ((? - shallow_size % ?) % ?) "
                        + "WHERE shallow_size % ? <> 0")) {
            st.setInt(1, alignment);
            st.setInt(2, alignment);
            st.setInt(3, alignment);
            st.setInt(4, alignment);
            st.executeUpdate();
        }
    }

    private static Map<Long, Integer> computeChainOopCounts(
            Map<Long, HprofRecord.ClassDump> classDumps) {
        Map<Long, Integer> memo = new HashMap<>(classDumps.size());
        for (Long classId : classDumps.keySet()) {
            chainOopCount(classId, classDumps, memo);
        }
        return memo;
    }

    private static int chainOopCount(
            long classId,
            Map<Long, HprofRecord.ClassDump> classDumps,
            Map<Long, Integer> memo) {
        Integer cached = memo.get(classId);
        if (cached != null) {
            return cached;
        }
        HprofRecord.ClassDump cd = classDumps.get(classId);
        if (cd == null) {
            memo.put(classId, 0);
            return 0;
        }
        int ownCount = 0;
        for (int type : cd.instanceFieldTypes()) {
            if (type == HprofTag.BasicType.OBJECT) {
                ownCount++;
            }
        }
        long superId = cd.superClassId();
        int total = superId == 0L
                ? ownCount
                : ownCount + chainOopCount(superId, classDumps, memo);
        memo.put(classId, total);
        return total;
    }

    // ---- Phase 4: stack frames + traces ---------------------------------

    /**
     * Persists buffered STACK_FRAME records into {@code stack_frame} and
     * STACK_TRACE records into {@code stack_trace_frame}. Runs immediately
     * after {@link #walkTopLevel} so {@code top.stringPool} is complete and
     * method / signature / source-file ids can be resolved inline. Frames
     * whose method-name id is missing from the pool get an "<unresolved-...>"
     * placeholder; source-file id 0 maps to NULL.
     */
    private static void writeStackTraces(DuckDBConnection conn, TopLevelData top) throws SQLException {
        if (!top.stackFrames.isEmpty()) {
            Map<Integer, String> classNameBySerial = buildClassNameBySerial(top);
            try (DuckDBAppender app = conn.createAppender("stack_frame")) {
                for (HprofRecord.StackFrame sf : top.stackFrames) {
                    app.beginRow();
                    app.append(sf.stackFrameId());
                    String className = classNameBySerial.getOrDefault(sf.classSerial(),
                            "<unresolved-class-serial:" + sf.classSerial() + ">");
                    app.append(className);
                    app.append(resolveString(top, sf.methodNameStringId(), "<unresolved-method>"));
                    app.append(resolveString(top, sf.methodSignatureStringId(), ""));
                    String sourceFile = sf.sourceFileNameStringId() == 0L
                            ? null
                            : resolveString(top, sf.sourceFileNameStringId(), null);
                    if (sourceFile == null) {
                        app.appendNull();
                    } else {
                        app.append(sourceFile);
                    }
                    app.append(sf.lineNumber());
                    app.endRow();
                }
            }
        }
        if (!top.stackTraces.isEmpty()) {
            try (DuckDBAppender app = conn.createAppender("stack_trace_frame")) {
                for (HprofRecord.StackTrace st : top.stackTraces) {
                    long[] frameIds = st.frameIds();
                    for (int idx = 0; idx < frameIds.length; idx++) {
                        app.beginRow();
                        app.append(st.traceSerial());
                        app.append(st.threadSerial());
                        app.append(idx);
                        app.append(frameIds[idx]);
                        app.endRow();
                    }
                }
            }
        }
    }

    private static String resolveString(TopLevelData top, long stringId, String fallback) {
        byte[] bytes = top.stringPool.get(stringId);
        if (bytes != null) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return fallback;
    }

    /**
     * Maps each {@code classSerial} (from LOAD_CLASS) to the user-facing class
     * name. STACK_FRAME records reference classes by serial, and we want a
     * resolved name even when the matching CLASS_DUMP is absent — common for
     * framework classes that appear on stacks but are never instantiated.
     */
    private static Map<Integer, String> buildClassNameBySerial(TopLevelData top) {
        Map<Integer, String> out = new HashMap<>(top.loadClassByClassId.size());
        for (HprofRecord.LoadClass lc : top.loadClassByClassId.values()) {
            String raw = resolveString(top, lc.nameStringId(),
                    "<unresolved-class-name:0x" + Long.toHexString(lc.nameStringId()) + ">");
            out.put(lc.classSerial(), ClassNameFormatter.userFacing(raw));
        }
        return out;
    }

    // ---- Phase 5: metadata + warnings ------------------------------------

    private static void writeDumpMetadata(
            DuckDBConnection conn, HprofMappedFile file, Clock clock,
            TopLevelData top, Counters counters, long warningCount, boolean truncated,
            boolean compressedOops) throws SQLException, IOException {
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
            app.append(compressedOops);
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
