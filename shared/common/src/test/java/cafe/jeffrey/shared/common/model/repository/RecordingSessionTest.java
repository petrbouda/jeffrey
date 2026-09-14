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

package cafe.jeffrey.shared.common.model.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSessionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant FINISHED_AT = CREATED_AT.plusSeconds(60);

    private static RepositoryFile file(Long size) {
        return new RepositoryFile(
                "file-1", "file-1", CREATED_AT, size,
                SupportedFile.JFR, RecordingStatus.FINISHED, null);
    }

    private static RecordingSession session(Instant finishedAt, RepositoryFile... files) {
        RecordingStatus status = finishedAt != null ? RecordingStatus.FINISHED : RecordingStatus.ACTIVE;
        return new RecordingSession(
                "session-1", "session-1", "inst-1", CREATED_AT, finishedAt,
                status, null, List.of(files), false);
    }

    @Nested
    class TotalSizeBytes {

        @Test
        void sumsFileSizesTreatingUnknownAsZero() {
            RecordingSession recordingSession = session(FINISHED_AT, file(100L), file(null), file(50L));

            assertEquals(150L, recordingSession.totalSizeBytes());
        }
    }

    @Nested
    class IsFailedEmpty {

        @Test
        void finishedSessionWithoutFilesIsFailed() {
            assertTrue(session(FINISHED_AT).isFailedEmpty());
        }

        @Test
        void finishedSessionWithOnlyZeroSizeFilesIsFailed() {
            assertTrue(session(FINISHED_AT, file(0L), file(null)).isFailedEmpty());
        }

        @Test
        void finishedSessionWithDataIsNotFailed() {
            assertFalse(session(FINISHED_AT, file(1L)).isFailedEmpty());
        }

        @Test
        void activeSessionWithZeroBytesIsNotFailed() {
            assertFalse(session(null).isFailedEmpty());
        }
    }
    @Nested
    class FinishedChunks {

        private static RepositoryFile chunk(String id, String name, SupportedFile type, Instant createdAt) {
            return new RepositoryFile(id, name, createdAt, 10L, type, RecordingStatus.FINISHED, Path.of(name));
        }

        @Test
        void listsOnlyTheFinishedChunksOldestFirst() {
            RepositoryFile later = chunk("c-2", "profile-2.jfr", SupportedFile.JFR, CREATED_AT.plusSeconds(30));
            RepositoryFile earlier = chunk("c-1", "profile-1.jfr.lz4", SupportedFile.JFR_LZ4, CREATED_AT);
            RepositoryFile live = new RepositoryFile(
                    "c-3", "profile-3.jfr", CREATED_AT.plusSeconds(60), 10L, SupportedFile.JFR, RecordingStatus.ACTIVE, null);
            RepositoryFile dump = chunk("d-1", "heap.hprof", SupportedFile.HEAP_DUMP, CREATED_AT);

            assertEquals(List.of(earlier, later), session(FINISHED_AT, later, dump, live, earlier).finishedChunks());
        }

        /**
         * While the hub compresses a chunk its raw and compressed forms lie side by side under
         * one id; the compressed one is written whole and moved into place, so it is the one
         * that is complete.
         */
        @Test
        void keepsOneFilePerIdPreferringTheHubsCompressedForm() {
            RepositoryFile raw = chunk("c-1", "profile-1.jfr", SupportedFile.JFR, CREATED_AT);
            RepositoryFile compressed = chunk("c-1", "profile-1.jfr.lz4", SupportedFile.JFR_LZ4, CREATED_AT.plusSeconds(600));

            assertEquals(List.of(compressed), session(FINISHED_AT, raw, compressed).finishedChunks());
            assertEquals(List.of(compressed), session(FINISHED_AT, compressed, raw).finishedChunks());
        }

        @Test
        void aChunkWithoutATimeGoesLastRatherThanFailingTheSort() {
            RepositoryFile timed = chunk("c-2", "profile-2.jfr", SupportedFile.JFR, CREATED_AT);
            RepositoryFile untimed = chunk("c-1", "profile-1.jfr", SupportedFile.JFR, null);

            assertEquals(List.of(timed, untimed), session(FINISHED_AT, untimed, timed).finishedChunks());
        }
    }
}
