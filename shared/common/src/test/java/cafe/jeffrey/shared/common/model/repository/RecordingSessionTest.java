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
                "file-1", "file-1", CREATED_AT, size, ManagedFile.JFR, null);
    }

    private static RepositoryFile recording(String name, Instant createdAt) {
        return new RepositoryFile(name, name, createdAt, 1L, ManagedFile.JFR, null);
    }

    private static RepositoryFile artifact(String name, Instant createdAt) {
        return new RepositoryFile(name, name, createdAt, 1L, ManagedFile.APP_LOG, null);
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
     * Which chunk the profiler is still writing. Everything downstream rests on it: the
     * compression job refuses to touch that file, both retention jobs refuse to delete it, no
     * download may include it, and reading it produces a truncated answer.
     */
    @Nested
    class OpenRecording {

        @Test
        void isTheNewestChunkOfALiveSession() {
            RecordingSession recordingSession = session(
                    null,
                    recording("c1", CREATED_AT),
                    recording("c3", CREATED_AT.plusSeconds(120)),
                    recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals("c3", recordingSession.openRecording().orElseThrow().name());
        }

        /**
         * The listing arrives sorted by filename for presentation, and the two orders part
         * company as soon as a file's name does not carry a parseable timestamp. Every reader of
         * these files goes by the timestamp, so this one does too.
         */
        @Test
        void isPickedByTimestampRatherThanByPositionInTheListing() {
            RecordingSession recordingSession = session(
                    null,
                    recording("zzz-oldest", CREATED_AT),
                    recording("aaa-newest", CREATED_AT.plusSeconds(60)));

            assertEquals("aaa-newest", recordingSession.openRecording().orElseThrow().name());
        }

        /**
         * A compressed archive is a recording file like any other and is a candidate here. What
         * keeps it from being mistaken for the open chunk is that it keeps the timestamp from its
         * own name when the hub rewrites it, rather than taking the time of the rewrite.
         */
        @Test
        void isNotTheArchiveWrittenBesideAnEarlierChunk() {
            RepositoryFile archive = new RepositoryFile(
                    "c1", "c1.jfr.lz4", CREATED_AT, 1L, ManagedFile.JFR_LZ4, null);
            RecordingSession recordingSession = session(
                    null, archive, recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals("c2", recordingSession.openRecording().orElseThrow().name());
        }

        @Test
        void isNeverAnArtifactEvenWhenItIsTheNewestFile() {
            RecordingSession recordingSession = session(
                    null,
                    recording("c1", CREATED_AT),
                    artifact("app.log", CREATED_AT.plusSeconds(120)));

            assertEquals("c1", recordingSession.openRecording().orElseThrow().name());
        }

        @Test
        void isEmptyForAFinishedSession() {
            RecordingSession recordingSession = session(
                    FINISHED_AT,
                    recording("c1", CREATED_AT),
                    recording("c2", CREATED_AT.plusSeconds(60)));

            assertTrue(recordingSession.openRecording().isEmpty());
            assertEquals(List.of("c1", "c2"), namesOf(recordingSession.finishedRecordings()));
        }

        @Test
        void isEmptyForALiveSessionThatHasNoRecordingYet() {
            assertTrue(session(null, artifact("app.log", CREATED_AT)).openRecording().isEmpty());
        }

        @Test
        void isEmptyForASessionLoadedWithoutFiles() {
            assertTrue(session(null).openRecording().isEmpty());
        }
    }

    @Nested
    class FinishedFiles {

        @Test
        void keepEveryFileButTheOpenChunk() {
            RecordingSession recordingSession = session(
                    null,
                    recording("c1", CREATED_AT),
                    recording("c2", CREATED_AT.plusSeconds(60)),
                    artifact("app.log", CREATED_AT.plusSeconds(120)));

            assertEquals(List.of("c1", "app.log"), namesOf(recordingSession.finishedFiles()));
        }

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
                    recording("c1", CREATED_AT),
                    recording("c3", CREATED_AT.plusSeconds(120)),
                    recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals("c3", recordingSession.latestFinishedRecording().orElseThrow().name());
        }

        @Test
        void skipsTheChunkStillBeingWritten() {
            RecordingSession recordingSession = session(
                    null,
                    recording("c1", CREATED_AT),
                    recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals("c1", recordingSession.latestFinishedRecording().orElseThrow().name());
        }

        @Test
        void ignoresArtifacts() {
            RecordingSession recordingSession = session(
                    FINISHED_AT,
                    recording("c1", CREATED_AT),
                    artifact("app.log", CREATED_AT.plusSeconds(120)));

            assertEquals("c1", recordingSession.latestFinishedRecording().orElseThrow().name());
        }

        @Test
        void isEmptyWhenNoChunkHasBeenClosedYet() {
            RecordingSession recordingSession = session(null, recording("c1", CREATED_AT));

            assertTrue(recordingSession.latestFinishedRecording().isEmpty());
        }

        @Test
        void isEmptyForASessionLoadedWithoutFiles() {
            assertTrue(session(FINISHED_AT).latestFinishedRecording().isEmpty());
        }
    }
}
