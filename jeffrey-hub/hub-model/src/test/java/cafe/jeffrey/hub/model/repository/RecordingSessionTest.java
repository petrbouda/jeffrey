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

    private static RepositoryFile file(long size) {
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

    /** The instance's newest session: no newer one has started. */
    private static RecordingSession newest(RepositoryFile... files) {
        return new RecordingSession("session-1", "session-1", "inst-1", CREATED_AT, true, List.of(files));
    }

    /** A session a newer one in its instance has ended. */
    private static RecordingSession ended(RepositoryFile... files) {
        return new RecordingSession("session-1", "session-1", "inst-1", CREATED_AT, false, List.of(files));
    }

    @Nested
    class TotalSizeBytes {

        @Test
        void sumsFileSizes() {
            RecordingSession recordingSession = ended(file(100L), file(50L));

            assertEquals(150L, recordingSession.totalSizeBytes());
        }
    }

    @Nested
    class IsFailedEmpty {

        @Test
        void anEndedSessionWithoutFilesIsFailed() {
            assertTrue(ended().isFailedEmpty());
        }

        @Test
        void anEndedSessionWithOnlyZeroSizeFilesIsFailed() {
            assertTrue(ended(file(0L), file(0L)).isFailedEmpty());
        }

        @Test
        void anEndedSessionWithDataIsNotFailed() {
            assertFalse(ended(file(1L)).isFailedEmpty());
        }

        @Test
        void theNewestSessionWithZeroBytesIsNotFailed() {
            assertFalse(newest().isFailedEmpty());
        }
    }

    /**
     * The one rule the hub and Microscope must agree on: which recording may still be on its way
     * onto the volume. Each side has its own copy of {@code RecordingSession}; these three cases
     * are the same in both tests, so a change to one side's answer fails here before it disagrees
     * in production.
     */
    @Nested
    class OpenRecording {

        @Test
        void isTheNewestRecordingOfTheInstancesNewestSession() {
            RepositoryFile older = recording("profile-1.jfr", CREATED_AT);
            RepositoryFile newer = recording("profile-2.jfr", CREATED_AT.plusSeconds(60));
            RecordingSession recordingSession = newest(older, newer);

            assertEquals(Optional.of(newer), recordingSession.openRecording());
            assertTrue(recordingSession.isOpen(newer));
            assertFalse(recordingSession.isOpen(older));
        }

        /**
         * The listing arrives sorted by filename for presentation, and the two orders part
         * company as soon as a file's name does not carry a parseable timestamp. Every reader of
         * these files goes by the timestamp, so this one does too.
         */
        @Test
        void isPickedByTimestampRatherThanByPositionInTheListing() {
            RecordingSession recordingSession = newest(
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
                    "c1", "c1.jfr.lz4", CREATED_AT, 1L, true, null);
            RecordingSession recordingSession = newest(
                    archive, recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals("c2", recordingSession.openRecording().orElseThrow().name());
        }

        @Test
        void isNeverAnArtifactHoweverNewItIs() {
            RepositoryFile chunk = recording("profile-1.jfr", CREATED_AT);
            RepositoryFile log = artifact("gc.jvm-log", CREATED_AT.plusSeconds(600));
            RecordingSession recordingSession = newest(chunk, log);

            assertEquals(Optional.of(chunk), recordingSession.openRecording());
        }

        @Test
        void isAbsentOnceANewerSessionHasStarted() {
            RepositoryFile chunk = recording("profile-1.jfr", CREATED_AT);
            RecordingSession recordingSession = ended(chunk);

            assertEquals(Optional.empty(), recordingSession.openRecording());
            assertFalse(recordingSession.isOpen(chunk));
        }

        @Test
        void isEmptyForANewestSessionThatHasNoRecordingYet() {
            assertTrue(newest(artifact("app.log", CREATED_AT)).openRecording().isEmpty());
        }

        @Test
        void isEmptyForASessionLoadedWithoutFiles() {
            assertTrue(newest().openRecording().isEmpty());
        }
    }

    @Nested
    class FinishedRecordings {

        @Test
        void areOrderedOldestFirstWhenNarrowedToRecordings() {
            RecordingSession recordingSession = ended(
                    recording("c3", CREATED_AT.plusSeconds(120)),
                    artifact("app.log", CREATED_AT),
                    recording("c1", CREATED_AT),
                    recording("c2", CREATED_AT.plusSeconds(60)));

            assertEquals(List.of("c1", "c2", "c3"), namesOf(recordingSession.finishedRecordings()));
        }
    }

    @Nested
    class LastDataAt {

        @Test
        void isTheNewestRecordingsTimestamp() {
            RecordingSession recordingSession = newest(
                    recording("c1", CREATED_AT.plusSeconds(60)),
                    recording("c2", CREATED_AT.plusSeconds(960)),
                    artifact("app.log", CREATED_AT.plusSeconds(1200)));

            assertEquals(CREATED_AT.plusSeconds(960), recordingSession.lastDataAt());
        }

        @Test
        void isTheStartWhileThereIsNoRecording() {
            assertEquals(CREATED_AT, newest(artifact("app.log", CREATED_AT.plusSeconds(30))).lastDataAt());
        }
    }
}
