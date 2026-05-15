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

import cafe.jeffrey.profile.heapdump.parser.parquet.ParquetSink;
import cafe.jeffrey.profile.heapdump.parser.parquet.ParquetStaging;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
import org.duckdb.DuckDBAppender;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.appendNullableId;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.appendNullableInt;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.primArrayClassId;

/**
 * Phase 5 — Pass B. Fused parallel walk that fans the instance / gc_root /
 * outbound_ref emission across {@code requestedWorkers} virtual threads.
 *
 * <p>Each worker owns its own in-memory DuckDB and three appenders (one per
 * output table); regions are round-robin partitioned so variable-sized regions
 * roughly average out. The coordinator bulk-loads each table from
 * {@code <stagingDir>/<table>/*.parquet} into the index DB once every worker
 * has flushed.
 *
 * <p>The frozen {@link ClassDumpIndex#byId()} from Pass A is shared read-only
 * with every worker. Per-worker {@link Counters} instances are merged into a
 * single {@link PassBOutput} after completion — counts summed, warning /
 * primitive-array maps unioned.
 */
public final class HprofPassBWalker {

    private static final String INSTANCE_STAGING_DDL =
            "instance_id BIGINT, class_id BIGINT, file_offset BIGINT, record_kind TINYINT, "
                    + "shallow_size INTEGER, array_length INTEGER, primitive_type TINYINT";

    private static final String GC_ROOT_STAGING_DDL =
            "instance_id BIGINT, root_kind TINYINT, thread_serial INTEGER, "
                    + "frame_index INTEGER, file_offset BIGINT";

    private static final String OUTBOUND_REF_STAGING_DDL =
            "source_id BIGINT, target_id BIGINT, field_kind TINYINT, field_id INTEGER";

    private static final String INSTANCE_TABLE = "instance";

    private static final String GC_ROOT_TABLE = "gc_root";

    private static final String OUTBOUND_REF_TABLE = "outbound_ref";

    private HprofPassBWalker() {
    }

    /**
     * Worker-local mutable counter bag. Each virtual thread accumulates its
     * slice's counts and per-record maps in its own instance; the orchestrator
     * sums them into a single {@link PassBOutput}.
     */
    private static final class Counters {
        final List<ParseWarning> warnings = new ArrayList<>();
        final Map<Long, PrimitiveArrayInfo> primitiveArrayInfoByArrayId = new HashMap<>();
        long instanceCount;
        long gcRootCount;
        long subRecordCount;
        long outboundRefCount;
    }

    public static PassBOutput walk(
            HprofMappedFile file, HeapDumpDatabaseClient client, TopLevelData top,
            ClassDumpIndex classes, int idSize, InstanceLayout layout,
            Path stagingDir, int requestedWorkers) throws IOException {
        Map<Long, HprofRecord.ClassDump> classesById = classes.byId();
        List<HprofRecord.HeapDumpRegion> regions = top.regions;
        int n = Math.max(1, Math.min(requestedWorkers, regions.size()));

        List<List<HprofRecord.HeapDumpRegion>> partitions = new ArrayList<>(n);
        for (int w = 0; w < n; w++) {
            partitions.add(new ArrayList<>());
        }
        for (int i = 0; i < regions.size(); i++) {
            partitions.get(i % n).add(regions.get(i));
        }

        try (ParquetStaging staging = ParquetStaging.open(stagingDir)) {
            staging.prepareTable(INSTANCE_TABLE);
            staging.prepareTable(GC_ROOT_TABLE);
            staging.prepareTable(OUTBOUND_REF_TABLE);

            List<Future<Counters>> futures = new ArrayList<>(n);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int w = 0; w < n; w++) {
                    List<HprofRecord.HeapDumpRegion> assigned = partitions.get(w);
                    Map<String, Path> outputs = staging.partFiles(
                            w, INSTANCE_TABLE, GC_ROOT_TABLE, OUTBOUND_REF_TABLE);
                    futures.add(executor.submit(() ->
                            runWorker(file, assigned, classesById, idSize, layout, outputs)));
                }
            }

            long instanceCount = 0L;
            long gcRootCount = 0L;
            long outboundRefCount = 0L;
            long subRecordCount = 0L;
            Map<Long, PrimitiveArrayInfo> primArrInfo = new HashMap<>();
            List<ParseWarning> warnings = new ArrayList<>();
            for (Future<Counters> f : futures) {
                Counters wc = FutureJoin.unwrap(f);
                instanceCount += wc.instanceCount;
                gcRootCount += wc.gcRootCount;
                outboundRefCount += wc.outboundRefCount;
                subRecordCount += wc.subRecordCount;
                primArrInfo.putAll(wc.primitiveArrayInfoByArrayId);
                warnings.addAll(wc.warnings);
            }

            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_INSTANCE, INSTANCE_TABLE);
            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_GC_ROOT, GC_ROOT_TABLE);
            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_OUTBOUND_REF, OUTBOUND_REF_TABLE);

            return new PassBOutput(instanceCount, gcRootCount, outboundRefCount, subRecordCount,
                    primArrInfo, warnings);
        }
    }

    private static Counters runWorker(
            HprofMappedFile file, List<HprofRecord.HeapDumpRegion> assigned,
            Map<Long, HprofRecord.ClassDump> classes, int idSize, InstanceLayout layout,
            Map<String, Path> outputs) {
        Map<String, String> ddls = new LinkedHashMap<>();
        ddls.put(INSTANCE_TABLE, INSTANCE_STAGING_DDL);
        ddls.put(GC_ROOT_TABLE, GC_ROOT_STAGING_DDL);
        ddls.put(OUTBOUND_REF_TABLE, OUTBOUND_REF_STAGING_DDL);

        Counters c = new Counters();
        try (ParquetSink sink = ParquetSink.open(ddls, outputs)) {
            DuckDBAppender instApp = sink.appender(INSTANCE_TABLE);
            DuckDBAppender rootApp = sink.appender(GC_ROOT_TABLE);
            DuckDBAppender refApp = sink.appender(OUTBOUND_REF_TABLE);
            long[] refCount = {0L};

            for (HprofRecord.HeapDumpRegion region : assigned) {
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
                                    case HprofRecord.InstanceDump id -> {
                                        appendInstanceFromInstanceDump(instApp, id, layout, c);
                                        refCount[0] += emitInstanceRefs(file, id, classes, idSize, refApp);
                                    }
                                    case HprofRecord.ObjectArrayDump oa -> {
                                        appendInstanceFromObjectArray(instApp, oa, layout, c);
                                        refCount[0] += emitArrayRefs(file, oa, idSize, refApp);
                                    }
                                    case HprofRecord.PrimitiveArrayDump pa ->
                                            appendInstanceFromPrimitiveArray(instApp, pa, idSize, layout, c);
                                    case HprofRecord.GcRoot root -> {
                                        appendGcRoot(rootApp, root);
                                        c.gcRootCount++;
                                    }
                                    case HprofRecord.ClassDump ignored -> {
                                        // Handled by Pass A (HprofClassDumpWalker).
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
            c.outboundRefCount = refCount[0];
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return c;
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

    private static void appendInstanceFromInstanceDump(
            DuckDBAppender app, HprofRecord.InstanceDump id, InstanceLayout layout, Counters c) throws SQLException {
        // Approximate JVM-side shallow size: object header + encoded fields.
        int shallow = layout.objectHeader() + id.instanceFieldsByteLength();
        app.beginRow();
        app.append(id.instanceId());
        appendNullableId(app, id.classId());
        app.append(id.fileOffset());
        app.append(HprofIndex.RECORD_KIND_INSTANCE);
        app.append(shallow);
        app.appendNull(); // array_length
        app.appendNull(); // primitive_type
        app.endRow();
        c.instanceCount++;
    }

    private static void appendInstanceFromObjectArray(
            DuckDBAppender app, HprofRecord.ObjectArrayDump oa, InstanceLayout layout, Counters c) throws SQLException {
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
        app.append(HprofIndex.RECORD_KIND_OBJECT_ARRAY);
        app.append(shallow);
        app.append(oa.length());
        app.appendNull(); // primitive_type
        app.endRow();
        c.instanceCount++;
    }

    private static void appendInstanceFromPrimitiveArray(
            DuckDBAppender app, HprofRecord.PrimitiveArrayDump pa, int idSize, InstanceLayout layout, Counters c)
            throws SQLException {
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
        // row seeded by Pass A so the instance joins to a real class name.
        app.append(primArrayClassId(pa.elementType()));
        app.append(pa.fileOffset());
        app.append(HprofIndex.RECORD_KIND_PRIMITIVE_ARRAY);
        app.append(shallow);
        app.append(pa.length());
        app.append((byte) pa.elementType());
        app.endRow();
        c.instanceCount++;
        c.primitiveArrayInfoByArrayId.put(
                pa.arrayId(),
                new PrimitiveArrayInfo(pa.fileOffset(), pa.length(), pa.elementType()));
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
}
