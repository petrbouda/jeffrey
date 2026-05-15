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

import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

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
                HprofShallowSizeCorrector.apply(client, classes.byId(), layout));

        // Materialise decoded java.lang.String content so OQL string predicates
        // push down to DuckDB varchar functions instead of decoding per-instance.
        Elapsed<Long> stringContentE = measureSql(() ->
                HprofStringContentWriter.write(client, file, top, classes, passB.primArrInfo(), idSize,
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


}
