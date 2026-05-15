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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.JavaStringDecoder;
import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.parser.parquet.ParquetSink;
import cafe.jeffrey.profile.heapdump.parser.parquet.ParquetStaging;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.duckdb.DuckDBAppender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.appendNullableId;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.appendNullableInt;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.primArrayClassId;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.primArrayName;

/**
 * Builds a DuckDB index ({@code .idx.duckdb}) for an HPROF heap dump.
 * <p>
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
 * <p>
 * Memory cost is dominated by the transient string pool plus the LoadClass map;
 * both are released as soon as the build completes.
 */
public final class HprofIndex {

    //language=sql
    private static final String INSTANCES_PER_CLASS_ID_SQL =
            "SELECT instance_id, file_offset FROM instance WHERE class_id = ?";

    /**
     * Bumped when the on-disk schema or extraction semantics change.
     */
    public static final String PARSER_VERSION = "0.1.0";

    // Schema-level constants for instance.record_kind. Package-private because
    // both the Pass B writer (HprofPassBWalker) and the shallow-size corrector
    // (HprofShallowSizeCorrector) reference them.
    static final byte RECORD_KIND_INSTANCE = 0;
    static final byte RECORD_KIND_OBJECT_ARRAY = 1;
    static final byte RECORD_KIND_PRIMITIVE_ARRAY = 2;

    /**
     * Heap below this size on a 64-bit dump is assumed to use compressed oops.
     * The JVM disables compressed oops once {@code -Xmx} exceeds 32 GiB (the
     * compressed-pointer encoding tops out there), so total .hprof size is a
     * reasonable proxy when no JFR-side hint is available.
     */
    private static final long COMPRESSED_OOPS_HEAP_LIMIT = 32L * 1024 * 1024 * 1024;

    /**
     * Result of an index-build run. Counts reflect rows actually written.
     *
     * <p>{@code subPhases} carries per-stage timings so the UI's "Building
     * indexes" accordion can show where the build's wall time actually went
     * (parsing top-level vs. walking heap regions vs. decoding Strings, etc.).
     */
    public record IndexResult(
            long stringCount,
            long classCount,
            long instanceCount,
            long gcRootCount,
            long outboundRefCount,
            long stringContentCount,
            long warningCount,
            boolean truncated,
            long bytesParsed,
            long recordCount,
            Duration buildTime,
            List<SubPhaseTiming> subPhases) {
    }

    private HprofIndex() {
    }

    /**
     * Builds (or rebuilds) the index next to {@code file} at {@code indexDbPath}.
     * Any existing index file is deleted first.
     */
    public static IndexResult build(HprofMappedFile file, Path indexDbPath, Clock clock)
            throws IOException, SQLException {
        return build(file, indexDbPath, clock, BuildOptions.defaults());
    }

    /**
     * Builds (or rebuilds) the index with custom {@link BuildOptions}.
     */
    public static IndexResult build(
            HprofMappedFile file, Path indexDbPath, Clock clock, BuildOptions options)
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
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }

        Files.deleteIfExists(indexDbPath);
        Files.deleteIfExists(HeapDumpIndexPaths.indexWalFor(file.path()));

        Path stagingDir = HeapDumpIndexPaths.indexStagingFor(file.path());
        Elapsed<IndexResult> elapsed = Measuring.s(() -> {
            try (HeapDumpIndexDb db = HeapDumpIndexDb.openAndInitialize(indexDbPath)) {
                return doBuild(file, db, clock, options, stagingDir);
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        });

        IndexResult r = elapsed.entity();
        return new IndexResult(
                r.stringCount(),
                r.classCount(),
                r.instanceCount(),
                r.gcRootCount(),
                r.outboundRefCount(),
                r.stringContentCount(),
                r.warningCount(),
                r.truncated(),
                r.bytesParsed(),
                r.recordCount(),
                elapsed.duration(),
                r.subPhases());
    }

    private static IndexResult doBuild(
            HprofMappedFile file, HeapDumpIndexDb db, Clock clock, BuildOptions options, Path stagingDir)
            throws IOException, SQLException {
        HeapDumpDatabaseClient client = db.databaseClient();
        int idSize = file.header().idSize();
        // Compressed-oops inference: 64-bit + heap below the JVM's 32 GiB threshold.
        // Total .hprof size is a coarse but reliable proxy for max heap.
        boolean compressedOops = (idSize == 8) && (file.size() < COMPRESSED_OOPS_HEAP_LIMIT);
        InstanceLayout layout = InstanceLayout.from(idSize, compressedOops);

        Elapsed<TopLevelData> topE = measureSql(() -> HprofTopLevelWalk.walk(file, client));
        TopLevelData top = topE.entity();

        Duration stackTracesD = measureSqlVoid(() -> HprofStackTraceWriter.write(client, top));

        // Drop every non-PK index before the bulk writes so per-row writes
        // skip the per-insert ART-tree updates. Recreated in bulk at the end,
        // which DuckDB executes far faster than 30 M incremental inserts.
        Duration dropIndexesD = measureSqlVoid(() -> HprofNonPkIndexes.dropAll(client));

        // Pass A — sequential, class-dumps only. Produces the read-only
        // ClassDumpIndex that Pass B and downstream phases share.
        Elapsed<ClassDumpIndex> classesE = measureSql(() -> HprofClassDumpWalker.walk(file, client, top));
        ClassDumpIndex classes = classesE.entity();

        // Pass B — parallel fused walk. N virtual-thread workers each take a
        // slice of `top.regions`, decode the records themselves, and stream
        // their rows into per-worker parquet shards (one in-memory DuckDB
        // appender per shard, so appender state stays thread-confined). The
        // coordinator then bulk-loads each table from {staging}/<table>/*.parquet
        // via DuckDB's parallel parquet reader.
        //
        // Fusing walkInstancesAndRoots + walkRegionsForRefs into one walk per
        // worker visits every region exactly once instead of twice — a free
        // win on top of the parallelism.
        Elapsed<PassBOutput> passBE = measureSql(() ->
                HprofPassBWalker.walk(file, client, top, classes, idSize, layout,
                        stagingDir, options.walkWorkers()));
        PassBOutput passB = passBE.entity();
        long warningCount = top.warnings.size() + classes.warnings().size() + passB.warnings().size();
        boolean truncated = ParseWarning.anyError(top.warnings)
                || ParseWarning.anyError(classes.warnings())
                || ParseWarning.anyError(passB.warnings());

        // Instance dumps are appended with the on-disk field-block size (idSize per
        // OOP, no alignment). Correct them now that the full class hierarchy is
        // known: subtract the compressed-oops over-count and round each instance
        // up to the JVM allocation boundary.
        Duration shallowCorrD = measureSqlVoid(() ->
                applyInstanceShallowCorrection(client, classes.byId(), layout));

        // Materialise decoded java.lang.String content so OQL string predicates
        // push down to DuckDB varchar functions instead of decoding per-instance.
        Elapsed<Long> stringContentE = measureSql(() ->
                writeStringContent(client, file, top, classes, passB.primArrInfo(), idSize,
                        options.stringContentThreshold(), stagingDir, options.walkWorkers()));
        long stringContentCount = stringContentE.entity();

        long totalRecordCount = top.recordCount + passB.subRecordCount();
        Duration metadataD = measureSqlVoid(() -> {
            HprofMetadataWriter.writeMetadata(client, file, clock, top, totalRecordCount,
                    passB.instanceCount(), classes.classCount(),
                    passB.gcRootCount(), passB.outboundRefCount(),
                    warningCount, truncated, compressedOops, PARSER_VERSION);
            HprofMetadataWriter.writeWarnings(client, top.warnings);
            HprofMetadataWriter.writeWarnings(client, classes.warnings());
            HprofMetadataWriter.writeWarnings(client, passB.warnings());
        });

        // Recreate the indexes we dropped at the start. Bulk index creation
        // over populated tables is dramatically faster than per-row inserts
        // into an existing index — DuckDB sorts once then walks rather than
        // doing 30 M individual ART-tree insertions. The indexes touch
        // different tables, so they parallelise cleanly across separate
        // connections to the same DuckDB file (DuckDB serialises ART writes
        // only within a single table).
        Duration createIndexesD = measureSqlVoid(() ->
                HprofNonPkIndexes.createAll(client, db.path(), options.walkWorkers()));

        // Force a checkpoint so all WAL contents land in the main DB file.
        // Without this, opening the file in read-only mode (HeapView) fails because
        // read-only connections cannot replay an outstanding WAL.
        Duration checkpointD = measureSqlVoid(() ->
                client.execute(HeapDumpStatement.CHECKPOINT, "CHECKPOINT"));

        List<SubPhaseTiming> subPhases = List.of(
                new SubPhaseTiming("walk_top_level", topE.duration().toMillis(),
                        top.stringCount + " strings"),
                new SubPhaseTiming("write_stack_traces", stackTracesD.toMillis(),
                        top.stackFrames.size() + " frames"),
                new SubPhaseTiming("drop_indexes", dropIndexesD.toMillis(), null),
                new SubPhaseTiming("walk_class_dumps", classesE.duration().toMillis(),
                        classes.classCount() + " classes"),
                new SubPhaseTiming("walk_pass_b", passBE.duration().toMillis(),
                        passB.instanceCount() + " inst, "
                                + passB.outboundRefCount() + " edges, "
                                + options.walkWorkers() + " workers"),
                new SubPhaseTiming("apply_shallow_correction", shallowCorrD.toMillis(), null),
                new SubPhaseTiming("write_string_content", stringContentE.duration().toMillis(),
                        stringContentCount + " strings"),
                new SubPhaseTiming("write_metadata", metadataD.toMillis(), null),
                new SubPhaseTiming("create_indexes", createIndexesD.toMillis(), null),
                new SubPhaseTiming("checkpoint", checkpointD.toMillis(), null));

        return new IndexResult(
                top.stringCount,
                classes.classCount(),
                passB.instanceCount(),
                passB.gcRootCount(),
                passB.outboundRefCount(),
                stringContentCount,
                warningCount,
                truncated,
                file.size(),
                totalRecordCount,
                Duration.ZERO, // overwritten by build()
                subPhases);
    }

    /**
     * {@link Measuring#s} adapter for phases that throw {@link SQLException} or
     * {@link IOException}. Mirrors the same helper in {@link DominatorTreeBuilder}.
     */
    private static <T> Elapsed<T> measureSql(SqlSupplier<T> body) {
        return Measuring.s(() -> {
            try {
                return body.get();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Duration measureSqlVoid(SqlRunnable body) {
        return Measuring.r(() -> {
            try {
                body.run();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException, IOException;
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException, IOException;
    }

    // ---- Phase 7: java.lang.String content materialisation ----------------

    // HPROF stores class names with slash separators; "java/lang/String" is the
    // canonical String type name as it appears in the string pool.
    static final String STRING_CLASS_HPROF_NAME = "java/lang/String";

    private static final String STRING_CONTENT_TABLE = "string_content";

    private static final String STRING_CONTENT_STAGING_DDL =
            "instance_id BIGINT, content_length INTEGER, content VARCHAR";

    /**
     * Decodes every {@code java.lang.String} instance and writes one row per
     * String to the {@code string_content} table. Content is set to {@code NULL}
     * when its decoded length exceeds {@code threshold} (so OQL string predicates
     * can detect uncovered Strings via {@code WHERE sc.content IS NULL} for the
     * opt-in scan-large-Strings fallback path).
     *
     * <p>Returns the number of rows written. A {@code threshold < 0} disables
     * the cap (every String is materialised in full).
     */
    private static long writeStringContent(
            HeapDumpDatabaseClient client, HprofMappedFile file, TopLevelData top,
            ClassDumpIndex classes, Map<Long, PrimitiveArrayInfo> primArrInfo,
            int idSize, int threshold,
            Path stagingDir, int requestedWorkers) throws SQLException, IOException {

        Long stringClassId = findClassIdByHprofName(top);
        if (stringClassId == null) {
            return 0;
        }
        HprofRecord.ClassDump cd = classes.byId().get(stringClassId);
        if (cd == null) {
            return 0;
        }

        int valueOffset = -1;
        int coderOffset = -1;
        long[] nameIds = cd.instanceFieldNameIds();
        int[] types = cd.instanceFieldTypes();
        int offset = 0;
        for (int i = 0; i < types.length; i++) {
            byte[] nameBytes = top.stringPool.get(nameIds[i]);
            String name = nameBytes == null
                    ? ""
                    : new String(nameBytes, StandardCharsets.UTF_8);
            int type = types[i];
            int size = HprofTypeSize.sizeOf(type, idSize);
            if ("value".equals(name) && type == HprofTag.BasicType.OBJECT) {
                valueOffset = offset;
            } else if ("coder".equals(name) && type == HprofTag.BasicType.BYTE) {
                coderOffset = offset;
            }
            offset += size;
        }
        if (valueOffset < 0) {
            return 0;
        }

        // Materialise every String instance's (id, file_offset) pair into compact
        // primitive arrays first. The decode + write phase then runs in parallel
        // virtual threads, each owning its own slice and parquet shard. Reading
        // the metadata up-front isolates DuckDB I/O on the main thread and lets
        // the per-row decode (which dominates on real heaps) parallelise cleanly.
        long[][] stringRows = loadStringInstanceRows(client, stringClassId);
        long[] instanceIds = stringRows[0];
        long[] fileOffsets = stringRows[1];
        int total = instanceIds.length;
        if (total == 0) {
            return 0L;
        }

        int n = Math.max(1, Math.min(requestedWorkers, total));
        int[] starts = new int[n];
        int[] ends = new int[n];
        int chunk = total / n;
        int extra = total - chunk * n;
        int cursor = 0;
        for (int w = 0; w < n; w++) {
            int size = chunk + (w < extra ? 1 : 0);
            starts[w] = cursor;
            ends[w] = cursor + size;
            cursor += size;
        }

        final int finalValueOffset = valueOffset;
        final int finalCoderOffset = coderOffset;
        Map<Long, PrimitiveArrayInfo> arrayInfoByArrayId = primArrInfo;

        long emitted;
        try (ParquetStaging staging = ParquetStaging.open(stagingDir)) {
            staging.prepareTable(STRING_CONTENT_TABLE);

            List<Future<Long>> futures = new ArrayList<>(n);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int w = 0; w < n; w++) {
                    int start = starts[w];
                    int end = ends[w];
                    Path outputPath = staging.partFile(STRING_CONTENT_TABLE, w);
                    futures.add(executor.submit(() -> runStringContentWorker(
                            file, instanceIds, fileOffsets, start, end,
                            finalValueOffset, finalCoderOffset, idSize,
                            arrayInfoByArrayId, threshold, outputPath)));
                }
            }

            emitted = 0L;
            for (Future<Long> f : futures) {
                emitted += FutureJoin.unwrap(f);
            }

            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_STRING_CONTENT, STRING_CONTENT_TABLE);
        }
        return emitted;
    }

    private static long[][] loadStringInstanceRows(HeapDumpDatabaseClient client, long stringClassId)
            throws SQLException {
        long count = client.queryLong(HeapDumpStatement.TOTAL_INSTANCE_COUNT,
                "SELECT COUNT(*) FROM instance WHERE class_id = ?", stringClassId);
        int total = (int) Math.min(count, Integer.MAX_VALUE);
        long[] instanceIds = new long[total];
        long[] fileOffsets = new long[total];
        int[] cursor = {0};
        try (PreparedStatement ps = client.connection().prepareStatement(INSTANCES_PER_CLASS_ID_SQL)) {
            ps.setLong(1, stringClassId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && cursor[0] < total) {
                    instanceIds[cursor[0]] = rs.getLong(1);
                    fileOffsets[cursor[0]] = rs.getLong(2);
                    cursor[0]++;
                }
            }
        }
        int filled = cursor[0];
        if (filled < total) {
            long[] trimmedIds = new long[filled];
            long[] trimmedOffsets = new long[filled];
            System.arraycopy(instanceIds, 0, trimmedIds, 0, filled);
            System.arraycopy(fileOffsets, 0, trimmedOffsets, 0, filled);
            return new long[][]{trimmedIds, trimmedOffsets};
        }
        return new long[][]{instanceIds, fileOffsets};
    }

    private static long runStringContentWorker(
            HprofMappedFile file, long[] instanceIds, long[] fileOffsets, int start, int end,
            int valueOffset, int coderOffset, int idSize,
            Map<Long, PrimitiveArrayInfo> arrayInfoByArrayId,
            int threshold, Path outputPath) {
        long emitted = 0L;
        try (ParquetSink sink = ParquetSink.open(
                Map.of(STRING_CONTENT_TABLE, STRING_CONTENT_STAGING_DDL),
                Map.of(STRING_CONTENT_TABLE, outputPath))) {
            DuckDBAppender app = sink.appender(STRING_CONTENT_TABLE);
            for (int i = start; i < end; i++) {
                long instanceId = instanceIds[i];
                long instOffset = fileOffsets[i];
                long fieldBlockStart = instOffset + 2L * idSize + 8L;

                long valueRef = file.readId(fieldBlockStart + valueOffset);
                Byte coder = coderOffset >= 0
                        ? file.readByte(fieldBlockStart + coderOffset)
                        : null;

                String content;
                if (valueRef == 0L) {
                    content = "";
                } else {
                    PrimitiveArrayInfo info = arrayInfoByArrayId.get(valueRef);
                    if (info == null) {
                        continue;
                    }
                    content = decodeStringForIndex(file, info, coder, idSize);
                    if (content == null) {
                        continue;
                    }
                }

                int len = content.length();
                app.beginRow();
                app.append(instanceId);
                app.append(len);
                if (threshold >= 0 && len > threshold) {
                    app.appendNull();
                } else {
                    app.append(content);
                }
                app.endRow();
                emitted++;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return emitted;
    }

    private static String decodeStringForIndex(
            HprofMappedFile file, PrimitiveArrayInfo info, Byte coder, int idSize) {
        int elementSize = HprofTypeSize.sizeOf(info.elementType(), idSize);
        if (elementSize < 0) {
            return null;
        }
        long payloadOffset = info.fileOffset() + idSize + 9L;
        long byteLengthLong = (long) info.arrayLength() * elementSize;
        if (byteLengthLong > Integer.MAX_VALUE) {
            byteLengthLong = Integer.MAX_VALUE;
        }
        byte[] bytes = file.readBytes(payloadOffset, (int) byteLengthLong);
        return JavaStringDecoder.decodeContent(bytes, info.elementType(), coder);
    }

    private static Long findClassIdByHprofName(TopLevelData top) {
        String userFacing = ClassNameFormatter.userFacing(HprofIndex.STRING_CLASS_HPROF_NAME);
        for (HprofRecord.LoadClass lc : top.loadClassByClassId.values()) {
            byte[] nameBytes = top.stringPool.get(lc.nameStringId());
            if (nameBytes == null) {
                continue;
            }
            String raw = new String(nameBytes, StandardCharsets.UTF_8);
            if (HprofIndex.STRING_CLASS_HPROF_NAME.equals(raw)
                    || userFacing.equals(ClassNameFormatter.userFacing(raw))) {
                return lc.classId();
            }
        }
        return null;
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
     * <p>
     * Both corrections collapse to no-ops when they're not needed:
     * uncompressed 64-bit / 32-bit heaps have {@code oopOverheadDelta() == 0},
     * and rows already aligned to the boundary are left untouched.
     */
    private static void applyInstanceShallowCorrection(
            HeapDumpDatabaseClient client,
            Map<Long, HprofRecord.ClassDump> classDumps,
            InstanceLayout layout) {

        int oopDelta = layout.oopOverheadDelta();
        int alignment = layout.objectAlignment();

        if (oopDelta > 0 && !classDumps.isEmpty()) {
            Map<Long, Integer> chainOopByClass = computeChainOopCounts(classDumps);
            client.execute(HeapDumpStatement.CREATE_TEMP_CLASS_CHAIN_OOP,
                    "CREATE TEMP TABLE _class_chain_oop (class_id BIGINT, oop_count INTEGER)");
            client.withAppender(HeapDumpStatement.APPEND_CLASS_CHAIN_OOP, "_class_chain_oop", app -> {
                long rows = 0;
                for (Map.Entry<Long, Integer> e : chainOopByClass.entrySet()) {
                    if (e.getValue() == 0) {
                        continue; // skip zero rows to keep the table tight
                    }
                    app.beginRow();
                    app.append(e.getKey());
                    app.append(e.getValue());
                    app.endRow();
                    rows++;
                }
                return rows;
            });
            client.update(HeapDumpStatement.UPDATE_INSTANCE_SHALLOW_SIZE_OOPS,
                    "UPDATE instance SET shallow_size = shallow_size - c.oop_count * ? "
                            + "FROM _class_chain_oop c "
                            + "WHERE instance.class_id = c.class_id "
                            + "  AND instance.record_kind = " + RECORD_KIND_INSTANCE,
                    oopDelta);
            client.execute(HeapDumpStatement.DROP_TEMP_CLASS_CHAIN_OOP, "DROP TABLE _class_chain_oop");
        }

        // Round every row up to objectAlignment. Arrays are already aligned by
        // appendInstance*, but instances were written without alignment and may
        // have just become unaligned again after the OOP-delta subtraction.
        // Use modular arithmetic (no division) so the math stays in INTEGER
        // domain — DuckDB's `/` promotes to floating point on bound parameters.
        client.update(HeapDumpStatement.UPDATE_INSTANCE_SHALLOW_SIZE_ALIGN,
                "UPDATE instance SET shallow_size = shallow_size + ((? - shallow_size % ?) % ?) "
                        + "WHERE shallow_size % ? <> 0",
                alignment, alignment, alignment, alignment);
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

}
