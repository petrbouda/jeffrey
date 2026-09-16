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

package cafe.jeffrey.hub.core.project.repository.file;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When a chunk was opened, which is the timestamp everything downstream orders a session by:
 * {@code ChunkWindow} tiles the recording with it, both retention jobs age files by it, and
 * which chunk the profiler still holds open is a maximum over it.
 */
class AsprofFileInfoProcessorTest {

    private static final Instant FILESYSTEM_TIME = Instant.parse("2020-01-01T00:00:00Z");

    /**
     * Stands in for the filesystem, so a test can tell "read out of the name" from "asked the
     * filesystem" by the answer alone.
     */
    private static final FileInfoProcessor FILESYSTEM = new FileInfoProcessor() {
        @Override
        public Comparator<Path> comparator() {
            return Comparator.comparing(Path::toString);
        }

        @Override
        public Instant createdAt(Path file) {
            return FILESYSTEM_TIME;
        }
    };

    private final AsprofFileInfoProcessor processor = new AsprofFileInfoProcessor(FILESYSTEM, "profile-");

    private Instant createdAtOf(String filename) {
        return processor.createdAt(Path.of("/sessions/session-1", filename));
    }

    @Nested
    class FromTheName {

        @Test
        void readsTheTimestampAChunkWasNamedAfter() {
            assertEquals(
                    Instant.parse("2026-02-20T12:05:00Z"),
                    createdAtOf("profile-20260220-120500.jfr"));
        }

        /**
         * The one that matters: the hub rewrites a closed chunk as {@code .jfr.lz4}, and the
         * archive is a new file whose creation time is when the hub compressed it — minutes to
         * hours after the profiler wrote the chunk, and later than the chunk still open beside
         * it. Read that way, the archive looks like the newest recording file of the session and
         * takes the open chunk's place: the compression job would then compress the file the
         * profiler is writing and delete the original, and both retention jobs would consider it
         * deletable. The name is the only source that survives the hub touching the file.
         */
        @Test
        void isUnchangedByTheHubCompressingTheChunk() {
            assertEquals(
                    createdAtOf("profile-20260220-120500.jfr"),
                    createdAtOf("profile-20260220-120500.jfr.lz4"));
        }
    }

    @Nested
    class FallingBackToTheFilesystem {

        @Test
        void forARecordingNamedWithAnotherPrefix() {
            assertEquals(FILESYSTEM_TIME, createdAtOf("recording-20260220-120500.jfr"));
        }

        /**
         * A timestamp that does not parse must fall back rather than throw. The caller drops a
         * file it cannot describe, so an exception here would take a single oddly named file out
         * of the listing entirely instead of merely sorting it imprecisely.
         */
        @Test
        void forATimestampThatDoesNotParse() {
            assertEquals(FILESYSTEM_TIME, createdAtOf("profile-not-a-timestamp.jfr"));
            assertEquals(FILESYSTEM_TIME, createdAtOf("profile-20260220-120500.jfr.lz4.lz4"));
        }

        @Test
        void forAFileThatIsNotAJfrAtAll() {
            assertEquals(FILESYSTEM_TIME, createdAtOf("service-app.log"));
            assertEquals(FILESYSTEM_TIME, createdAtOf("heap.hprof"));
        }
    }
}
