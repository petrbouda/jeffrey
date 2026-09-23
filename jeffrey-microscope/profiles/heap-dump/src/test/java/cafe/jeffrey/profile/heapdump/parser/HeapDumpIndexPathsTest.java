/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.profile.heapdump.parser;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;

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
