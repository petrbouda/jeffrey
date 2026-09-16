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
                SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);
    }

    private static RepositoryFile recording(String name, Instant createdAt, RecordingStatus status) {
        return new RepositoryFile(
                name, name, createdAt, 1L, SupportedRecordingFile.JFR, status, null);
    }

    private static RepositoryFile artifact(String name, Instant createdAt) {
        return new RepositoryFile(
                name, name, createdAt, 1L, SupportedRecordingFile.APP_LOG, RecordingStatus.FINISHED, null);
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

    /**
     * Which chunk carries the session's one-shot configuration events, and — once the session has
     * finished — its {@code jdk.Shutdown}.
     */
    @Nested
    class LatestFinishedRecording {

        @Test
        void takesTheNewestClosedChunk() {
            RecordingSession recordingSession = session(
                    FINISHED_AT,
                    recording("c1", CREATED_AT, RecordingStatus.FINISHED),
                    recording("c3", CREATED_AT.plusSeconds(120), RecordingStatus.FINISHED),
                    recording("c2", CREATED_AT.plusSeconds(60), RecordingStatus.FINISHED));

            assertEquals("c3", recordingSession.latestFinishedRecording().orElseThrow().name());
        }

        @Test
        void skipsTheChunkStillBeingWritten() {
            // Reading an open chunk is what produces a truncated answer, so the newest CLOSED one
            // is the answer even though a newer file exists.
            RecordingSession recordingSession = session(
                    null,
                    recording("c1", CREATED_AT, RecordingStatus.FINISHED),
                    recording("c2", CREATED_AT.plusSeconds(60), RecordingStatus.ACTIVE));

            assertEquals("c1", recordingSession.latestFinishedRecording().orElseThrow().name());
        }

        @Test
        void ignoresArtifacts() {
            RecordingSession recordingSession = session(
                    FINISHED_AT,
                    recording("c1", CREATED_AT, RecordingStatus.FINISHED),
                    artifact("app.log", CREATED_AT.plusSeconds(120)));

            assertEquals("c1", recordingSession.latestFinishedRecording().orElseThrow().name());
        }

        @Test
        void isEmptyWhenNoChunkHasBeenClosedYet() {
            RecordingSession recordingSession = session(
                    null, recording("c1", CREATED_AT, RecordingStatus.ACTIVE));

            assertTrue(recordingSession.latestFinishedRecording().isEmpty());
        }

        @Test
        void isEmptyForASessionLoadedWithoutFiles() {
            assertTrue(session(FINISHED_AT).latestFinishedRecording().isEmpty());
        }
    }

}
