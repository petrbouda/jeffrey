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
package cafe.jeffrey.profile.heapdump.persistence;

import java.nio.file.Path;

/**
 * Resolves sibling artifact paths for a heap dump file.
 *
 * The index DuckDB file lives next to the .hprof, mirroring the slot previously
 * occupied by NetBeans' .nbcache directory. Deleting the .hprof should be
 * accompanied by deleting these siblings.
 */
public final class HeapDumpIndexPaths {

    static final String INDEX_SUFFIX = ".idx.duckdb";
    static final String INDEX_WAL_SUFFIX = ".idx.duckdb.wal";
    static final String INDEX_STAGING_SUFFIX = ".idx.staging";
    static final String INDEX_BUILD_SUFFIX = ".idx.building.duckdb";
    static final String WAL_SUFFIX = ".wal";

    private HeapDumpIndexPaths() {
    }

    /**
     * The DuckDB index file for the given heap dump.
     * Example: /profiles/p1/heap-dump/recording.hprof -> /profiles/p1/heap-dump/recording.hprof.idx.duckdb
     */
    public static Path indexFor(Path hprof) {
        return sibling(hprof, INDEX_SUFFIX);
    }

    /** The DuckDB write-ahead log sibling, used when cleaning up. */
    public static Path indexWalFor(Path hprof) {
        return sibling(hprof, INDEX_WAL_SUFFIX);
    }

    /**
     * Scratch path an index build writes to before publishing the result under
     * {@link #indexFor(Path)}. Readers judge an index by little more than "the
     * file is there and out-dates the dump", so a half-populated database must
     * never occupy that name — building here and renaming on success keeps a
     * failed (or killed) build from leaving one behind.
     */
    public static Path indexBuildFor(Path hprof) {
        return sibling(hprof, INDEX_BUILD_SUFFIX);
    }

    /** The DuckDB write-ahead log sibling of any index database path. */
    public static Path walFor(Path indexDb) {
        return sibling(indexDb, WAL_SUFFIX);
    }

    /**
     * Sibling directory used as a transient staging area where parallel build
     * workers write parquet shards before the coordinator bulk-loads them into
     * the index DB. Deleted at the end of the build.
     */
    public static Path indexStagingFor(Path hprof) {
        return sibling(hprof, INDEX_STAGING_SUFFIX);
    }

    /**
     * Same staging directory as {@link #indexStagingFor(Path)}, but derived
     * from the {@code .idx.duckdb} path instead of the {@code .hprof} path.
     * Used by builders (dominator tree, persist) that only carry the index-DB
     * handle.
     */
    public static Path stagingForIndex(Path indexDb) {
        if (indexDb == null) {
            throw new IllegalArgumentException("indexDb must not be null");
        }
        Path fileName = indexDb.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("indexDb must have a file name: path=" + indexDb);
        }
        String name = fileName.toString();
        String stem = name.endsWith(INDEX_SUFFIX)
                ? name.substring(0, name.length() - INDEX_SUFFIX.length())
                : name;
        return indexDb.resolveSibling(stem + INDEX_STAGING_SUFFIX);
    }

    private static Path sibling(Path hprof, String suffix) {
        if (hprof == null) {
            throw new IllegalArgumentException("hprof must not be null");
        }
        Path fileName = hprof.getFileName();
        if (fileName == null || fileName.toString().isEmpty()) {
            throw new IllegalArgumentException("hprof must have a non-empty file name: path=" + hprof);
        }
        return hprof.resolveSibling(fileName + suffix);
    }
}
