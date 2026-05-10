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
