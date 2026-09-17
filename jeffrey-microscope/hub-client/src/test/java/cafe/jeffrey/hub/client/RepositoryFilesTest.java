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
