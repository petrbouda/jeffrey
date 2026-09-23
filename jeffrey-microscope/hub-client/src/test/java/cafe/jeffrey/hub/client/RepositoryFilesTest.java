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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryFilesTest {

    private static final Instant CREATED_AT = Instant.parse("2026-02-20T12:00:00Z");
    private static final long SIZE = 2048L;

    private static RepositoryFile file(String name, boolean recording) {
        return new RepositoryFile("id-" + name, name, CREATED_AT, SIZE, recording, null);
    }

    @Nested
    class IsArtifact {

        @Test
        void aRecordingChunkIsNotAnArtifact() {
            RepositoryFile chunk = file("profile-1.jfr", true);

            assertFalse(RepositoryFiles.isArtifact(chunk));
        }

        /**
         * The hub used to refuse async-profiler's rotation cache file itself; that refusal moved
         * here with the rest of the file-type domain. The hub reports it as a plain, non-recording
         * file it lists and serves as it lies, and Microscope is the side that recognises the name
         * as {@link ManagedFile#ASPROF_TEMP} and declines to treat it as anything fetchable.
         */
        @Test
        void theProfilersCacheFileIsNotAnArtifact() {
            RepositoryFile cacheFile = file("profile-1.jfr.1~", false);

            assertFalse(RepositoryFiles.isArtifact(cacheFile));
        }

        @Test
        void aJvmLogIsAnArtifact() {
            RepositoryFile log = file("gc.jvm-log", false);

            assertTrue(RepositoryFiles.isArtifact(log));
        }
    }

    @Nested
    class TypeOf {

        @Test
        void classifiesFromTheName() {
            RepositoryFile heapDump = file("heap.hprof", false);

            assertEquals(ManagedFile.HEAP_DUMP, RepositoryFiles.typeOf(heapDump));
        }
    }
}
