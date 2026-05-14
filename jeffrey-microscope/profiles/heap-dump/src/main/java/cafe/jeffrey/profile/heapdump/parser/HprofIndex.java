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
import java.sql.ResultSet;
import java.sql.SQLException;
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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.JavaStringDecoder;
import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
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

    /**
     * Tunable options for the index build. Currently controls only the
     * {@link #stringContentThreshold} — the maximum decoded character length
     * of a {@code java.lang.String} whose content is materialised into the
     * {@code string_content} table (-1 means unlimited).
     */
    public record BuildOptions(int stringContentThreshold) {

        public static final int DEFAULT_STRING_CONTENT_THRESHOLD = 4096;

        public static BuildOptions defaults() {
            return new BuildOptions(DEFAULT_STRING_CONTENT_THRESHOLD);
        }
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

        Elapsed<IndexResult> elapsed = Measuring.s(() -> {
            try (HeapDumpIndexDb db = HeapDumpIndexDb.openAndInitialize(indexDbPath)) {
                return doBuild(file, db, clock, options);
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        });

        IndexResult r = elapsed.entity();
        IndexResult withTime = new IndexResult(
                r.stringCount(), r.classCount(), r.instanceCount(), r.gcRootCount(),
                r.outboundRefCount(), r.stringContentCount(), r.warningCount(), r.truncated(),
                r.bytesParsed(), r.recordCount(), elapsed.duration(), r.subPhases());
        LOG.debug("Heap dump index built: path={} indexPath={} duration_ms={} strings={} classes={} instances={} gc_roots={} outbound_refs={} string_contents={} warnings={}",
                file.path(), indexDbPath, elapsed.duration().toMillis(),
                withTime.stringCount(), withTime.classCount(), withTime.instanceCount(),
                withTime.gcRootCount(), withTime.outboundRefCount(),
                withTime.stringContentCount(), withTime.warningCount());
        return withTime;
    }

    private static IndexResult doBuild(HprofMappedFile file, HeapDumpIndexDb db, Clock clock, BuildOptions options)
            throws IOException, SQLException {
        HeapDumpDatabaseClient client = db.databaseClient();
        DuckDBConnection conn = db.connection();
        int idSize = file.header().idSize();
        // Compressed-oops inference: 64-bit + heap below the JVM's 32 GiB threshold.
        // Total .hprof size is a coarse but reliable proxy for max heap.
        boolean compressedOops = (idSize == 8) && (file.size() < COMPRESSED_OOPS_HEAP_LIMIT);
        InstanceLayout layout = InstanceLayout.from(idSize, compressedOops);

        Elapsed<TopLevelData> topE = measureSql(() -> walkTopLevel(file, client));
        TopLevelData top = topE.entity();

        Duration stackTracesD = measureSqlVoid(() -> writeStackTraces(client, top));

        // Drop every non-PK index before the bulk writes so per-row writes
        // skip the per-insert ART-tree updates. Recreated in bulk at the end,
        // which DuckDB executes far faster than 30 M incremental inserts.
        Duration dropIndexesD = measureSqlVoid(() -> dropNonPkIndexes(client));

        // Pass A — sequential, class-dumps only. Populates classDumpsById,
        // which Pass B reads as a frozen map. Fast on real heaps (~22 K classes).
        Elapsed<Counters> classesE = measureSql(() -> walkClassDumps(file, client, top));
        Counters counters = classesE.entity();

        // Pass B is run sequentially (NOT in parallel). DuckDB's JDBC
        // appender API can't safely have two concurrent appenders sharing a
        // single connection — the previous parallel implementation lost ~90 %
        // of instance rows on a 7.6 M-instance heap because the second thread
        // created an appender that disturbed the first's transaction state.
        // The split into two methods is kept because: (1) it gives clear
        // per-phase timings in the UI, and (2) it's the right shape for a
        // future multi-connection parallelisation if/when we decide to take
        // that on.
        Elapsed<Void> instE = measureSql(() -> {
            walkInstancesAndRoots(file, client, top, idSize, layout, counters);
            return null;
        });
        Elapsed<Long> refsE = measureSql(() ->
                walkRegionsForRefs(file, client, top, counters.classDumpsById, idSize, counters));
        long warningCount = top.warnings.size() + counters.warnings.size();
        boolean truncated = anyError(top.warnings) || anyError(counters.warnings);

        // Instance dumps are appended with the on-disk field-block size (idSize per
        // OOP, no alignment). Correct them now that the full class hierarchy is
        // known: subtract the compressed-oops over-count and round each instance
        // up to the JVM allocation boundary.
        Duration shallowCorrD = measureSqlVoid(() ->
                applyInstanceShallowCorrection(client, counters.classDumpsById, layout));

        // Materialise decoded java.lang.String content so OQL string predicates
        // push down to DuckDB varchar functions instead of decoding per-instance.
        Elapsed<Long> stringContentE = measureSql(() ->
                writeStringContent(client, file, top, counters, idSize, options.stringContentThreshold()));
        long stringContentCount = stringContentE.entity();

        Duration metadataD = measureSqlVoid(() -> {
            writeDumpMetadata(client, file, clock, top, counters, warningCount, truncated, compressedOops);
            writeParseWarnings(client, top.warnings);
            writeParseWarnings(client, counters.warnings);
        });

        // Recreate the indexes we dropped at the start. Bulk index creation
        // over populated tables is dramatically faster than per-row inserts
        // into an existing index — DuckDB sorts once then walks rather than
        // doing 30 M individual ART-tree insertions.
        Duration createIndexesD = measureSqlVoid(() -> createNonPkIndexes(client));

        // Force a checkpoint so all WAL contents land in the main DB file.
        // Without this, opening the file in read-only mode (HeapView) fails because
        // read-only connections cannot replay an outstanding WAL.
        Duration checkpointD = measureSqlVoid(() ->
                client.execute(HeapDumpStatement.CHECKPOINT, "CHECKPOINT"));

        LOG.debug(
                "Building indexes phases: walk_top_level_ms={} write_stack_traces_ms={} "
                        + "drop_indexes_ms={} walk_class_dumps_ms={} walk_instances_and_roots_ms={} "
                        + "walk_regions_for_refs_ms={} apply_shallow_correction_ms={} "
                        + "write_string_content_ms={} write_metadata_ms={} create_indexes_ms={} "
                        + "checkpoint_ms={} strings={} classes={} instances={} gc_roots={} "
                        + "outbound_refs={} string_contents={}",
                topE.duration().toMillis(),
                stackTracesD.toMillis(),
                dropIndexesD.toMillis(),
                classesE.duration().toMillis(),
                instE.duration().toMillis(),
                refsE.duration().toMillis(),
                shallowCorrD.toMillis(),
                stringContentE.duration().toMillis(),
                metadataD.toMillis(),
                createIndexesD.toMillis(),
                checkpointD.toMillis(),
                top.stringCount,
                counters.classCount,
                counters.instanceCount,
                counters.gcRootCount,
                counters.outboundRefCount,
                stringContentCount);

        List<SubPhaseTiming> subPhases = List.of(
                new SubPhaseTiming("walk_top_level", topE.duration().toMillis(),
                        top.stringCount + " strings"),
                new SubPhaseTiming("write_stack_traces", stackTracesD.toMillis(),
                        top.stackFrames.size() + " frames"),
                new SubPhaseTiming("drop_indexes", dropIndexesD.toMillis(), null),
                new SubPhaseTiming("walk_class_dumps", classesE.duration().toMillis(),
                        counters.classCount + " classes"),
                new SubPhaseTiming("walk_instances_and_roots", instE.duration().toMillis(),
                        counters.instanceCount + " instances"),
                new SubPhaseTiming("walk_regions_for_refs", refsE.duration().toMillis(),
                        counters.outboundRefCount + " edges"),
                new SubPhaseTiming("apply_shallow_correction", shallowCorrD.toMillis(), null),
                new SubPhaseTiming("write_string_content", stringContentE.duration().toMillis(),
                        stringContentCount + " strings"),
                new SubPhaseTiming("write_metadata", metadataD.toMillis(), null),
                new SubPhaseTiming("create_indexes", createIndexesD.toMillis(), null),
                new SubPhaseTiming("checkpoint", checkpointD.toMillis(), null));

        return new IndexResult(
                top.stringCount,
                counters.classCount,
                counters.instanceCount,
                counters.gcRootCount,
                counters.outboundRefCount,
                stringContentCount,
                warningCount,
                truncated,
                file.size(),
                top.recordCount + counters.subRecordCount,
                Duration.ZERO, // overwritten by build()
                subPhases);
    }

    private static final String[] NON_PK_INDEX_DROP_DDL = {
            "DROP INDEX IF EXISTS idx_outbound_source",
            "DROP INDEX IF EXISTS idx_outbound_target",
            "DROP INDEX IF EXISTS idx_instance_class",
            "DROP INDEX IF EXISTS idx_gc_root_instance",
            "DROP INDEX IF EXISTS idx_class_name",
            "DROP INDEX IF EXISTS idx_class_super",
            "DROP INDEX IF EXISTS idx_class_is_array",
            "DROP INDEX IF EXISTS idx_stack_trace_frame_thread"
    };

    private static final String[] NON_PK_INDEX_CREATE_DDL = {
            "CREATE INDEX IF NOT EXISTS idx_outbound_source ON outbound_ref(source_id)",
            "CREATE INDEX IF NOT EXISTS idx_outbound_target ON outbound_ref(target_id)",
            "CREATE INDEX IF NOT EXISTS idx_instance_class ON instance(class_id)",
            "CREATE INDEX IF NOT EXISTS idx_gc_root_instance ON gc_root(instance_id)",
            "CREATE INDEX IF NOT EXISTS idx_class_name ON class(name)",
            "CREATE INDEX IF NOT EXISTS idx_class_super ON class(super_class_id)",
            "CREATE INDEX IF NOT EXISTS idx_class_is_array ON class(is_array)",
            "CREATE INDEX IF NOT EXISTS idx_stack_trace_frame_thread ON stack_trace_frame(thread_serial)"
    };

    /**
     * Drops every non-PK index DuckDB maintains for this heap-dump index DB.
     * Called before the bulk-load phases so per-row writes don't incur per-insert
     * ART-tree updates. Recreated in bulk by {@link #createNonPkIndexes} once
     * all rows are present.
     */
    private static void dropNonPkIndexes(HeapDumpDatabaseClient client) {
        for (String ddl : NON_PK_INDEX_DROP_DDL) {
            client.execute(HeapDumpStatement.DROP_INDEXES, ddl);
        }
    }

    /**
     * Recreates the indexes dropped by {@link #dropNonPkIndexes}. Bulk index
     * creation over a fully populated table is dramatically faster than
     * inserting 30 M rows into an existing index — DuckDB sorts the source
     * column once and walks rather than performing 30 M individual ART-tree
     * insertions.
     */
    private static void createNonPkIndexes(HeapDumpDatabaseClient client) {
        for (String ddl : NON_PK_INDEX_CREATE_DDL) {
            client.execute(HeapDumpStatement.CREATE_INDEXES, ddl);
        }
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

    private static TopLevelData walkTopLevel(HprofMappedFile file, HeapDumpDatabaseClient client) {
        TopLevelData data = new TopLevelData();
        client.withAppender(HeapDumpStatement.APPEND_STRING, "string", stringApp -> {
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
            return data.stringCount;
        });
        return data;
    }

    // ---- Pass 2: heap dump regions ---------------------------------------

    private static final class Counters {
        final List<ParseWarning> warnings = new ArrayList<>();
        /** classId → ClassDump, populated during the first region walk and consumed during the second. */
        final Map<Long, HprofRecord.ClassDump> classDumpsById = new HashMap<>();
        /**
         * arrayId → primitive array metadata, populated for every {@link HprofRecord.PrimitiveArrayDump}
         * during {@link #walkRegions}. Consumed by {@link #writeStringContent} to avoid an N-times
         * PK lookup against {@code instance} (~150 µs/round-trip × 1–2 M Strings on real heaps).
         */
        final Map<Long, PrimitiveArrayInfo> primitiveArrayInfoByArrayId = new HashMap<>();
        long classCount;
        long instanceCount;
        long gcRootCount;
        long subRecordCount;
        long outboundRefCount;
    }

    /**
     * In-memory metadata the indexer needs to decode every {@code java.lang.String}'s
     * backing primitive array: same triplet that {@link #writeStringContent} would
     * otherwise read with a SQL PK lookup. Held only during the index build.
     */
    private record PrimitiveArrayInfo(long fileOffset, int arrayLength, int elementType) {
    }

    /**
     * Pass A — class-dump only. Walks every region but processes only
     * CLASS_DUMP sub-records, writing the {@code class} and
     * {@code class_instance_field} tables and populating
     * {@link Counters#classDumpsById}. Cheap (~22 K class records on a
     * typical 7.6 M-instance heap) and must finish before Pass B reads the
     * map. Returns a fresh {@link Counters} that Pass B then mutates.
     */
    private static Counters walkClassDumps(
            HprofMappedFile file, HeapDumpDatabaseClient client, TopLevelData top) {
        Counters c = new Counters();
        Set<Long> writtenClassIds = new HashSet<>();

        client.withAppenderPair(HeapDumpStatement.APPEND_CLASS, "class", "class_instance_field",
                (classApp, fieldApp) -> {
                    // Seed synthetic primitive-array class rows so PRIMITIVE_ARRAY_DUMP
                    // instances can join to a real class name.
                    c.classCount += appendSyntheticPrimitiveArrayClasses(classApp);

                    for (HprofRecord.HeapDumpRegion region : top.regions) {
                        HprofSubRecordReader.read(file, region.fileOffset(), region.byteLength(),
                                new HprofSubRecordReader.Listener() {
                                    @Override
                                    public void onRecord(HprofRecord.Sub sub) {
                                        if (sub instanceof HprofRecord.ClassDump cd) {
                                            try {
                                                if (writtenClassIds.add(cd.classId())) {
                                                    appendClass(classApp, cd, top);
                                                    appendInstanceFields(fieldApp, cd, top);
                                                    c.classCount++;
                                                    c.classDumpsById.put(cd.classId(), cd);
                                                }
                                            } catch (SQLException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }

                                    @Override
                                    public void onWarning(ParseWarning warning) {
                                        c.warnings.add(warning);
                                    }
                                });
                    }
                    return c.classCount;
                });
        return c;
    }

    /**
     * Pass B (half 1) — non-class heap-dump records: writes the {@code instance}
     * and {@code gc_root} tables and populates {@link Counters#primitiveArrayInfoByArrayId}.
     * Designed to run concurrently with {@link #walkRegionsForRefs}: it touches
     * only its own {@link DuckDBAppender}s and only mutates the {@code c}
     * counters dedicated to non-ref data (instance / gc_root / primitive-array
     * map / subRecordCount). Reads no shared mutable state.
     */
    private static void walkInstancesAndRoots(
            HprofMappedFile file, HeapDumpDatabaseClient client, TopLevelData top, int idSize,
            InstanceLayout layout, Counters c) {
        client.withAppenderPair(HeapDumpStatement.APPEND_INSTANCE, "instance", "gc_root",
                (instApp, rootApp) -> {
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
                                            case HprofRecord.ClassDump ignored -> {
                                                // Already handled by Pass A (walkClassDumps).
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
                    return c.instanceCount + c.gcRootCount;
                });
    }

    /**
     * Pass B (half 2) — outbound_ref emission. Decodes INSTANCE_DUMP field bytes
     * against each class's instance-field chain and walks OBJECT_ARRAY_DUMP
     * element ids. Only emits refs whose target id is non-zero (HPROF id 0
     * means "no reference"). Designed to run concurrently with
     * {@link #walkInstancesAndRoots}: it reads only the frozen
     * {@code classes} map and {@code top.regions} list, and writes only its
     * own {@code outbound_ref} appender + the {@code outboundRefCount} field.
     */
    private static long walkRegionsForRefs(
            HprofMappedFile file, HeapDumpDatabaseClient client, TopLevelData top,
            Map<Long, HprofRecord.ClassDump> classes, int idSize, Counters c) {
        long[] count = {0L};
        client.withAppender(HeapDumpStatement.APPEND_OUTBOUND_REF, "outbound_ref", refApp -> {
            // Iterate the regions captured in walkTopLevel — no need to re-walk
            // the top-level (which the previous version did unnecessarily).
            for (HprofRecord.HeapDumpRegion region : top.regions) {
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
            return count[0];
        });
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

    // HPROF stores class names with slash separators; "java/lang/String" is the
    // canonical String type name as it appears in the string pool.
    private static final String STRING_CLASS_HPROF_NAME = "java/lang/String";

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
            Counters counters, int idSize, int threshold) throws SQLException {

        Long stringClassId = findClassIdByHprofName(top, STRING_CLASS_HPROF_NAME);
        if (stringClassId == null) {
            return 0;
        }
        HprofRecord.ClassDump cd = counters.classDumpsById.get(stringClassId);
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

        final int finalValueOffset = valueOffset;
        final int finalCoderOffset = coderOffset;
        final long finalStringClassId = stringClassId;
        Map<Long, PrimitiveArrayInfo> arrayInfoByArrayId = counters.primitiveArrayInfoByArrayId;
        long[] countBox = {0L};
        // The query against `instance` is part of the index-build pipeline; the
        // outer JFR event covers the entire string-content materialisation phase
        // (both the read scan and the appender writes). We use the raw connection
        // here because the appender body needs the result-set rows interleaved
        // with the per-row appends, which the queryStream API doesn't model.
        client.withAppender(HeapDumpStatement.APPEND_STRING_CONTENT, "string_content", app -> {
            try (PreparedStatement stringQuery = client.connection().prepareStatement(
                    "SELECT instance_id, file_offset FROM instance WHERE class_id = ?")) {
                stringQuery.setLong(1, finalStringClassId);
                try (ResultSet rs = stringQuery.executeQuery()) {
                    while (rs.next()) {
                        long instanceId = rs.getLong(1);
                        long instOffset = rs.getLong(2);
                        long fieldBlockStart = instOffset + 2L * idSize + 8L;

                        long valueRef = file.readId(fieldBlockStart + finalValueOffset);
                        Byte coder = finalCoderOffset >= 0
                                ? Byte.valueOf(file.readByte(fieldBlockStart + finalCoderOffset))
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
                        countBox[0]++;
                    }
                }
            }
            return countBox[0];
        });
        return countBox[0];
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

    private static Long findClassIdByHprofName(TopLevelData top, String hprofName) {
        String userFacing = ClassNameFormatter.userFacing(hprofName);
        for (HprofRecord.LoadClass lc : top.loadClassByClassId.values()) {
            byte[] nameBytes = top.stringPool.get(lc.nameStringId());
            if (nameBytes == null) {
                continue;
            }
            String raw = new String(nameBytes, StandardCharsets.UTF_8);
            if (hprofName.equals(raw) || userFacing.equals(ClassNameFormatter.userFacing(raw))) {
                return lc.classId();
            }
        }
        return null;
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

    // ---- Phase 4: stack frames + traces ---------------------------------

    /**
     * Persists buffered STACK_FRAME records into {@code stack_frame} and
     * STACK_TRACE records into {@code stack_trace_frame}. Runs immediately
     * after {@link #walkTopLevel} so {@code top.stringPool} is complete and
     * method / signature / source-file ids can be resolved inline. Frames
     * whose method-name id is missing from the pool get an "<unresolved-...>"
     * placeholder; source-file id 0 maps to NULL.
     */
    private static void writeStackTraces(HeapDumpDatabaseClient client, TopLevelData top) {
        if (!top.stackFrames.isEmpty()) {
            Map<Integer, String> classNameBySerial = buildClassNameBySerial(top);
            client.withAppender(HeapDumpStatement.APPEND_STACK_FRAME, "stack_frame", app -> {
                long rows = 0;
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
                    rows++;
                }
                return rows;
            });
        }
        if (!top.stackTraces.isEmpty()) {
            client.withAppender(HeapDumpStatement.APPEND_STACK_TRACE_FRAME, "stack_trace_frame", app -> {
                long rows = 0;
                for (HprofRecord.StackTrace st : top.stackTraces) {
                    long[] frameIds = st.frameIds();
                    for (int idx = 0; idx < frameIds.length; idx++) {
                        app.beginRow();
                        app.append(st.traceSerial());
                        app.append(st.threadSerial());
                        app.append(idx);
                        app.append(frameIds[idx]);
                        app.endRow();
                        rows++;
                    }
                }
                return rows;
            });
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
            HeapDumpDatabaseClient client, HprofMappedFile file, Clock clock,
            TopLevelData top, Counters counters, long warningCount, boolean truncated,
            boolean compressedOops) throws IOException {
        long mtimeMs = Files.getLastModifiedTime(file.path()).toMillis();
        client.withAppender(HeapDumpStatement.APPEND_DUMP_METADATA, "dump_metadata", app -> {
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
            return 1L;
        });
    }

    private static void writeParseWarnings(HeapDumpDatabaseClient client, List<ParseWarning> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        client.withAppender(HeapDumpStatement.APPEND_PARSE_WARNING, "parse_warning", app -> {
            long rows = 0;
            for (ParseWarning w : warnings) {
                app.beginRow();
                app.append(w.fileOffset());
                appendNullableInt(app, w.recordKind() == null ? null : w.recordKind());
                app.append((byte) w.severity().ordinal());
                app.append(w.message());
                app.endRow();
                rows++;
            }
            return rows;
        });
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
