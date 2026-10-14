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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
/**
 * The disk budget of a retention pass. Every session here is young and few, so the age windows
 * and the per-instance cap never act and only the quota decides.
 */
class ProjectRetentionJobQuotaTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final long MB = 1024L * 1024L;
    private static final long BUDGET = 100 * MB;

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    RepositoryStorage storage;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    @Mock
    ProjectInstanceRepository instanceRepository;

    @Mock
    StorageOverviewCache storageOverview;

    private ProjectRetentionJob job;

    @BeforeEach
    void setUp() {
        ProjectInfo projectInfo = new ProjectInfo(
                "proj-1", null, "my-project", "ws-1", NOW, NOW, Map.of());

        when(projectManager.info()).thenReturn(projectInfo);
        when(projectManager.repositoryManager()).thenReturn(repositoryManager);
        when(repositoryManager.deleteRecordingSession(anyString())).thenReturn(true);
        when(projectManager.repositoryStorage()).thenReturn(storage);
        when(projectManager.projectInstanceRepository()).thenReturn(instanceRepository);

        job = job(BUDGET + "");
    }

    private ProjectRetentionJob job(String maxSize) {
        return new ProjectRetentionJob(workspacesManager, config(maxSize), storageOverview, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JobConfig config(String maxSize) {
        return new JobConfig(true, Duration.ofMinutes(5), Map.of(
                "session-retention", "3650d",
                "active-session-file-retention", "3650d",
                "max-sessions", "1000",
                "max-size", maxSize,
                "instance-retention", "14d"));
    }

    private void execute() {
        job.retain(projectManager);
    }

    /**
     * A chunk. Whether it is the one the profiler still holds open is not said here: the session
     * decides, and the answer is its newest chunk while it records.
     */
    private static RepositoryFile recording(String id, Instant createdAt, long size) {
        return new RepositoryFile(id, id, createdAt, size, true, null);
    }

    private static RecordingSession finishedSession(
            String id, Instant createdAt, boolean retained, RepositoryFile... files) {

        return new RecordingSession(
                id, id, "inst-1", createdAt, createdAt.plusSeconds(60),
                RecordingStatus.FINISHED, List.of(files), retained);
    }

    private static RecordingSession activeSession(String id, Instant createdAt, RepositoryFile... files) {
        return new RecordingSession(
                id, id, "inst-1", createdAt, null,
                RecordingStatus.ACTIVE, List.of(files), false);
    }

    @Nested
    class UnderBudget {

        @Test
        void deletesNothing() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    finishedSession("s1", NOW.minusSeconds(3600), false,
                            recording("f1", NOW.minusSeconds(3600), 10 * MB))));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString());
            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }
    }

    @Nested
    class OverBudget {

        @Test
        void deletesOldestFinishedSessionsUntilUnderBudget() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    finishedSession("oldest", NOW.minusSeconds(9000), false,
                            recording("f1", NOW.minusSeconds(9000), 60 * MB)),
                    finishedSession("middle", NOW.minusSeconds(6000), false,
                            recording("f2", NOW.minusSeconds(6000), 60 * MB)),
                    finishedSession("newest", NOW.minusSeconds(3000), false,
                            recording("f3", NOW.minusSeconds(3000), 60 * MB))));

            execute();

            // 180MB total against a 100MB budget: dropping the two oldest brings it to 60MB
            ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
            verify(repositoryManager, org.mockito.Mockito.times(2))
                    .deleteRecordingSession(deleted.capture());

            assertEquals(List.of("oldest", "middle"), deleted.getAllValues());
        }

        @Test
        void stopsAsSoonAsItFitsTheBudget() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    finishedSession("oldest", NOW.minusSeconds(9000), false,
                            recording("f1", NOW.minusSeconds(9000), 80 * MB)),
                    finishedSession("newest", NOW.minusSeconds(3000), false,
                            recording("f2", NOW.minusSeconds(3000), 40 * MB))));

            execute();

            verify(repositoryManager).deleteRecordingSession("oldest");
            verify(repositoryManager, never()).deleteRecordingSession("newest");
        }

        /**
         * A session gone between the listing and the delete freed nothing. Counted as reclaimed
         * anyway, the job stopped early and reported the project within budget while it was not.
         */
        @Test
        void doesNotCountASessionThatWasAlreadyGone() {
            RecordingSession vanished = finishedSession("vanished", NOW.minusSeconds(4000), false,
                    recording("v", NOW.minusSeconds(4000), 80 * MB));
            RecordingSession next = finishedSession("next", NOW.minusSeconds(3000), false,
                    recording("n", NOW.minusSeconds(3000), 80 * MB));
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(vanished, next));
            when(repositoryManager.deleteRecordingSession("vanished")).thenReturn(false);

            execute();

            verify(repositoryManager).deleteRecordingSession("next");
        }

        @Test
        void neverDeletesRetainedSessions() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    finishedSession("pinned", NOW.minusSeconds(9000), true,
                            recording("f1", NOW.minusSeconds(9000), 90 * MB)),
                    finishedSession("normal", NOW.minusSeconds(3000), false,
                            recording("f2", NOW.minusSeconds(3000), 90 * MB))));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession("pinned");
            verify(repositoryManager).deleteRecordingSession("normal");
        }

        @Test
        void trimsActiveSessionOnlyAfterFinishedSessionsAreExhausted() {
            RepositoryFile oldChunk = recording("chunk-old", NOW.minusSeconds(2000), 70 * MB);
            RepositoryFile liveChunk = recording("chunk-live", NOW.minusSeconds(100), 60 * MB);

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    activeSession("live", NOW.minusSeconds(2500), oldChunk, liveChunk)));

            execute();

            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(storage).deleteRepositoryFiles(eq("live"), captor.capture());

            assertEquals(List.of("chunk-old"), captor.getValue(),
                    "The chunk the profiler is still writing must never be a candidate");
        }

        @Test
        void leavesLiveChunkAloneEvenWhenStillOverBudget() {
            RepositoryFile liveChunk = recording("chunk-live", NOW.minusSeconds(100), 500 * MB);

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    activeSession("live", NOW.minusSeconds(2500), liveChunk)));

            execute();

            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }

        /**
         * Two instances of one project record at once. Both sessions are live, so both are
         * trimmed — the older one first — and neither loses the chunk its profiler holds open.
         */
        @Test
        void trimsEveryLiveSessionOldestFirstAndKeepsEachOpenChunk() {
            RepositoryFile firstOld = recording("first-old", NOW.minusSeconds(3000), 60 * MB);
            RepositoryFile firstOpen = recording("first-open", NOW.minusSeconds(200), 60 * MB);
            RepositoryFile secondOld = recording("second-old", NOW.minusSeconds(1500), 60 * MB);
            RepositoryFile secondOpen = recording("second-open", NOW.minusSeconds(100), 60 * MB);

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    activeSession("second", NOW.minusSeconds(1600), secondOld, secondOpen),
                    activeSession("first", NOW.minusSeconds(3100), firstOld, firstOpen)));

            execute();

            verify(storage).deleteRepositoryFiles("first", List.of("first-old"));
            verify(storage).deleteRepositoryFiles("second", List.of("second-old"));
            verify(storage, never()).deleteRepositoryFiles(eq("first"), eq(List.of("first-open")));
            verify(storage, never()).deleteRepositoryFiles(eq("second"), eq(List.of("second-open")));
        }

        @Test
        void doesNotTrimRetainedActiveSession() {
            RepositoryFile oldChunk = recording("chunk-old", NOW.minusSeconds(2000), 200 * MB);

            RecordingSession pinnedActive = new RecordingSession(
                    "live", "live", "inst-1", NOW.minusSeconds(2500), null,
                    RecordingStatus.ACTIVE, List.of(oldChunk), true);

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(pinnedActive));

            execute();

            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }
    }

    @Nested
    class DegenerateInput {

        @Test
        void toleratesEmptyRepository() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of());

            assertDoesNotThrow(ProjectRetentionJobQuotaTest.this::execute);
        }
    }

    @Nested
    class BudgetValidation {

        @Test
        void rejectsNonPositiveBudget() {
            assertThrows(IllegalArgumentException.class, () -> job("0"));
        }

        @Test
        void readsBinaryUnits() {
            assertDoesNotThrow(() -> job("20G"));
        }
    }
}
