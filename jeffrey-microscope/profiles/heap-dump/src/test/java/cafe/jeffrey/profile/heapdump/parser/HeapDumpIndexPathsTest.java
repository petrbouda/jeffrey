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
import java.nio.file.Paths;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeapDumpIndexPathsTest {

    @Nested
    class IndexFor {

        @Test
        void appendsSiblingSuffix() {
            Path hprof = Paths.get("/profiles/p1/heap-dump/recording.hprof");
            assertEquals(
                    Paths.get("/profiles/p1/heap-dump/recording.hprof.idx.duckdb"),
                    HeapDumpIndexPaths.indexFor(hprof));
        }

        @Test
        void preservesExtensionRegardlessOfDumpName() {
            Path hprof = Paths.get("/tmp/dump-2026.hprof");
            assertEquals(
                    Paths.get("/tmp/dump-2026.hprof.idx.duckdb"),
                    HeapDumpIndexPaths.indexFor(hprof));
        }

        @Test
        void rejectsNullPath() {
            assertThrows(IllegalArgumentException.class, () -> HeapDumpIndexPaths.indexFor(null));
        }

        @Test
        void rejectsEmptyPath() {
            Path empty = Paths.get("");
            assertThrows(IllegalArgumentException.class, () -> HeapDumpIndexPaths.indexFor(empty));
        }
    }

    @Nested
    class IndexWalFor {

        @Test
        void appendsWalSuffix() {
            Path hprof = Paths.get("/profiles/p1/heap-dump/recording.hprof");
            assertEquals(
                    Paths.get("/profiles/p1/heap-dump/recording.hprof.idx.duckdb.wal"),
                    HeapDumpIndexPaths.indexWalFor(hprof));
        }
    }
}
