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

package cafe.jeffrey.hub.model.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSessionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant FINISHED_AT = CREATED_AT.plusSeconds(60);

    private static RepositoryFile file(Long size) {
        return new RepositoryFile(
                "file-1", "file-1", CREATED_AT, size, true, null);
    }

    private static RepositoryFile recording(String name, Instant createdAt) {
        return new RepositoryFile(name, name, createdAt, 1L, true, null);
    }

    private static RepositoryFile artifact(String name, Instant createdAt) {
        return new RepositoryFile(name, name, createdAt, 1L, false, null);
    }

    private static List<String> namesOf(List<RepositoryFile> files) {
        return files.stream().map(RepositoryFile::name).toList();
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
     * The one rule the hub and Microscope must agree on: which chunk the profiler still holds
     * open. Each side has its own copy of {@code RecordingSession}; these three cases are the
     * same in both tests, so a change to one side's answer fails here before it disagrees in
     * production.
     */
    @Nested
    class OpenRecording {

        @Test
        void isTheNewestRecordingOfASessionStillRecording() {
            RepositoryFile older = recording("profile-1.jfr", CREATED_AT);
            RepositoryFile newer = recording("profile-2.jfr", CREATED_AT.plusSeconds(60));
            RecordingSession recordingSession = session(null, older, newer);

            assertEquals(Optional.of(newer), recordingSession.openRecording());
            assertTrue(recordingSession.isOpen(newer));
            assertFalse(recordingSession.isOpen(older));
        }

        @Test
        void isNeverAnArtifactHoweverNewItIs() {
            RepositoryFile chunk = recording("profile-1.jfr", CREATED_AT);
            RepositoryFile log = artifact("gc.jvm-log", CREATED_AT.plusSeconds(600));
            RecordingSession recordingSession = session(null, chunk, log);

            assertEquals(Optional.of(chunk), recordingSession.openRecording());
        }

        @Test
        void isAbsentOnceTheSessionHasFinished() {
            RepositoryFile chunk = recording("profile-1.jfr", CREATED_AT);
            RecordingSession recordingSession = session(FINISHED_AT, chunk);

            assertEquals(Optional.empty(), recordingSession.openRecording());
            assertFalse(recordingSession.isOpen(chunk));
        }
    }

    @Nested
    class FinishedRecordings {

        @Test
        void areOrderedOldestFirstWhenNarrowedToRecordings() {
            RecordingSession recordingSession = session(
                    FINISHED_AT,
                    recording("c3", CREATED_AT.plusSeconds(120)),
                    artifact("app.log", CREATED_AT),
                    recording("c1", CREATED_AT),
                    recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals(List.of("c1", "c2", "c3"), namesOf(recordingSession.finishedRecordings()));
        }
    }
}
