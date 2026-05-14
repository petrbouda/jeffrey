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
package cafe.jeffrey.profile.heapdump.parser.parquet;

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Owns the per-build staging directory where parallel workers drop their
 * parquet shards before the coordinator bulk-loads them into the index DB.
 *
 * <p>Layout:
 * <pre>
 *   &lt;indexDbPath&gt;.idx.staging/
 *       instance/part_0.parquet, part_1.parquet, ...
 *       gc_root/part_0.parquet, part_1.parquet, ...
 *       outbound_ref/...
 *       ...
 * </pre>
 *
 * <p>Workers obtain output paths via {@link #partFile}. The coordinator merges
 * shards back into the index DB via {@link #bulkLoad} and clears each table's
 * subdirectory via {@link #clearTable} once a table has been fully loaded
 * (frees disk pressure during the build). The entire staging directory is
 * removed by {@link #close}, whether the build succeeded or failed.
 */
public final class ParquetStaging implements AutoCloseable {

    private final Path stagingDir;

    private boolean closed;

    private ParquetStaging(Path stagingDir) {
        this.stagingDir = stagingDir;
    }

    /**
     * Opens a fresh staging directory at {@code stagingDir}. Any pre-existing
     * contents (left over from a previously failed build) are deleted first;
     * heap-dump indexing serialises per-{@code hprof}, so there is never a
     * concurrent writer.
     */
    public static ParquetStaging open(Path stagingDir) throws IOException {
        if (stagingDir == null) {
            throw new IllegalArgumentException("stagingDir must not be null");
        }
        deleteRecursively(stagingDir);
        Files.createDirectories(stagingDir);
        return new ParquetStaging(stagingDir);
    }

    /** The output path for the {@code partIdx}-th shard of {@code table}. */
    public Path partFile(String table, int partIdx) {
        return tableDir(table).resolve("part_" + partIdx + ".parquet");
    }

    /**
     * Convenience for the common multi-table worker setup: builds a map from
     * table name to its {@code part_<partIdx>.parquet} path under this staging
     * directory. The map preserves insertion order so callers can rely on
     * deterministic flush order in {@link ParquetSink#close()}.
     */
    public Map<String, Path> partFiles(int partIdx, String... tables) {
        Map<String, Path> out = new LinkedHashMap<>();
        for (String table : tables) {
            out.put(table, partFile(table, partIdx));
        }
        return out;
    }

    /**
     * Ensures the per-table subdirectory exists before workers start writing
     * into it. Idempotent. Called once per table from the coordinator before
     * the worker fanout, since workers themselves write file outputs through
     * DuckDB's COPY (which needs the parent directory to exist).
     */
    public void prepareTable(String table) throws IOException {
        Files.createDirectories(tableDir(table));
    }

    /**
     * Bulk-loads every shard for {@code table} into the index DB via DuckDB's
     * parallel parquet reader. No-op if the table subdirectory is missing or
     * empty (e.g. a phase that produced zero rows).
     */
    public long bulkLoad(HeapDumpDatabaseClient client, HeapDumpStatement stmt, String table) {
        Path dir = tableDir(table);
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        if (!hasParquetShards(dir)) {
            return 0L;
        }
        String glob = dir.toAbsolutePath() + "/*.parquet";
        return client.bulkLoadFromParquet(stmt, table, glob);
    }

    /**
     * Removes every shard for {@code table} once it has been bulk-loaded into
     * the index DB. Coordinators call this immediately after the corresponding
     * {@link #bulkLoad} to reclaim disk during the build (the staging files for
     * heavy tables can be tens of GB on large dumps).
     */
    public void clearTable(String table) throws IOException {
        deleteRecursively(tableDir(table));
    }

    /** The staging directory root (used by tests and logging). */
    public Path directory() {
        return stagingDir;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        deleteRecursively(stagingDir);
    }

    private Path tableDir(String table) {
        return stagingDir.resolve(table);
    }

    private static boolean hasParquetShards(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.parquet")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
        }
    }
}
