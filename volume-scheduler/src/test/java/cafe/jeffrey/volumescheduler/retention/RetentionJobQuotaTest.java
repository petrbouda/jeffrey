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

package cafe.jeffrey.volumescheduler.retention;

import cafe.jeffrey.volumescheduler.storage.LatestStorageOverview;
import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.RecordingSessionFile;
import cafe.jeffrey.volumescheduler.volume.VolumeProject;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The disk budget of a retention pass. Every session here is young and few, so the age windows
 * and the per-instance cap never act and only the quota decides.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionJobQuotaTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final long MB = 1024L * 1024L;
    private static final long BUDGET = 100 * MB;

    @Mock
    VolumeProject project;

    @Mock
    LatestStorageOverview storageOverview;

    private RetentionJob job;

    @BeforeEach
    void setUp() {
        when(project.ref()).thenReturn(new ProjectRef("ws-1", "proj-1"));
        when(project.deleteSession(anyString())).thenReturn(SessionDeletion.SESSION_DELETED);

        job = job(BUDGET);
    }

    private RetentionJob job(long maxSize) {
        return new RetentionJob(
                mock(VolumeProjects.class), policy(maxSize), storageOverview, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RetentionPolicy policy(long maxSize) {
        return new RetentionPolicy(Duration.ofDays(3650), 1000, Duration.ofDays(3650), maxSize, Duration.ofDays(14));
    }

    private void execute() {
        job.retain(project);
    }

    /**
     * A chunk. Whether it may still be on its way onto the volume is not said here: the session
     * decides, and the answer is its newest chunk while it is its instance's newest session.
     */
    private static RecordingSessionFile recording(String id, Instant createdAt, long size) {
        return new RecordingSessionFile(id, id, createdAt, size, true, null);
    }

    /** A session a newer one in its instance has ended. */
    private static RecordingSession endedSession(String id, Instant createdAt, RecordingSessionFile... files) {
        return new RecordingSession(id, "inst-1", createdAt, false, List.of(files));
    }

    /** The newest session of {@code instanceId}. */
    private static RecordingSession newestSession(
            String id, String instanceId, Instant createdAt, RecordingSessionFile... files) {

        return new RecordingSession(id, instanceId, createdAt, true, List.of(files));
    }

    @Nested
    class UnderBudget {

        @Test
        void deletesNothing() {
            when(project.sessions()).thenReturn(List.of(
                    endedSession("s1", NOW.minusSeconds(3600),
                            recording("f1", NOW.minusSeconds(3600), 10 * MB))));

            execute();

            verify(project, never()).deleteSession(anyString());
            verify(project, never()).deleteFiles(anyString(), any());
        }
    }

    @Nested
    class OverBudget {

        @Test
        void deletesOldestFinishedSessionsUntilUnderBudget() {
            when(project.sessions()).thenReturn(List.of(
                    endedSession("oldest", NOW.minusSeconds(9000),
                            recording("f1", NOW.minusSeconds(9000), 60 * MB)),
                    endedSession("middle", NOW.minusSeconds(6000),
                            recording("f2", NOW.minusSeconds(6000), 60 * MB)),
                    endedSession("newest", NOW.minusSeconds(3000),
                            recording("f3", NOW.minusSeconds(3000), 60 * MB))));

            execute();

            // 180MB total against a 100MB budget: dropping the two oldest brings it to 60MB
            ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
            verify(project, times(2))
                    .deleteSession(deleted.capture());

            assertEquals(List.of("oldest", "middle"), deleted.getAllValues());
        }

        @Test
        void stopsAsSoonAsItFitsTheBudget() {
            when(project.sessions()).thenReturn(List.of(
                    endedSession("oldest", NOW.minusSeconds(9000),
                            recording("f1", NOW.minusSeconds(9000), 80 * MB)),
                    endedSession("newest", NOW.minusSeconds(3000),
                            recording("f2", NOW.minusSeconds(3000), 40 * MB))));

            execute();

            verify(project).deleteSession("oldest");
            verify(project, never()).deleteSession("newest");
        }

        /**
         * A session gone between the listing and the delete freed nothing. Counted as reclaimed
         * anyway, the job stopped early and reported the project within budget while it was not.
         */
        @Test
        void doesNotCountASessionThatWasAlreadyGone() {
            RecordingSession vanished = endedSession("vanished", NOW.minusSeconds(4000),
                    recording("v", NOW.minusSeconds(4000), 80 * MB));
            RecordingSession next = endedSession("next", NOW.minusSeconds(3000),
                    recording("n", NOW.minusSeconds(3000), 80 * MB));
            when(project.sessions()).thenReturn(List.of(vanished, next));
            when(project.deleteSession("vanished")).thenReturn(SessionDeletion.NOT_FOUND);

            execute();

            verify(project).deleteSession("next");
        }

        @Test
        void trimsTheNewestSessionOnlyAfterEndedSessionsAreExhausted() {
            RecordingSessionFile oldChunk = recording("chunk-old", NOW.minusSeconds(2000), 70 * MB);
            RecordingSessionFile liveChunk = recording("chunk-live", NOW.minusSeconds(100), 60 * MB);

            when(project.sessions()).thenReturn(List.of(
                    newestSession("live", "inst-1", NOW.minusSeconds(2500), oldChunk, liveChunk)));

            execute();

            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(project).deleteFiles(eq("live"), captor.capture());

            assertEquals(List.of("chunk-old"), captor.getValue(),
                    "The recording that may still be on its way must never be a candidate");
        }

        @Test
        void leavesTheNewestRecordingAloneEvenWhenStillOverBudget() {
            RecordingSessionFile liveChunk = recording("chunk-live", NOW.minusSeconds(100), 500 * MB);

            when(project.sessions()).thenReturn(List.of(
                    newestSession("live", "inst-1", NOW.minusSeconds(2500), liveChunk)));

            execute();

            verify(project, never()).deleteFiles(anyString(), any());
        }

        /**
         * Two instances of one project record at once. Both sessions are their instance's newest,
         * so both are trimmed — the older one first — and neither loses its newest recording.
         */
        @Test
        void trimsEveryNewestSessionOldestFirstAndKeepsEachNewestRecording() {
            RecordingSessionFile firstOld = recording("first-old", NOW.minusSeconds(3000), 60 * MB);
            RecordingSessionFile firstOpen = recording("first-open", NOW.minusSeconds(200), 60 * MB);
            RecordingSessionFile secondOld = recording("second-old", NOW.minusSeconds(1500), 60 * MB);
            RecordingSessionFile secondOpen = recording("second-open", NOW.minusSeconds(100), 60 * MB);

            when(project.sessions()).thenReturn(List.of(
                    newestSession("second", "inst-2", NOW.minusSeconds(1600), secondOld, secondOpen),
                    newestSession("first", "inst-1", NOW.minusSeconds(3100), firstOld, firstOpen)));

            execute();

            verify(project).deleteFiles("first", List.of("first-old"));
            verify(project).deleteFiles("second", List.of("second-old"));
            verify(project, never()).deleteFiles(eq("first"), eq(List.of("first-open")));
            verify(project, never()).deleteFiles(eq("second"), eq(List.of("second-open")));
        }
    }

    @Nested
    class DegenerateInput {

        @Test
        void toleratesEmptyRepository() {
            when(project.sessions()).thenReturn(List.of());

            assertDoesNotThrow(RetentionJobQuotaTest.this::execute);
        }
    }

    @Nested
    class BudgetValidation {

        @Test
        void rejectsNonPositiveBudget() {
            assertThrows(IllegalArgumentException.class, () -> job(0));
        }
    }
}
