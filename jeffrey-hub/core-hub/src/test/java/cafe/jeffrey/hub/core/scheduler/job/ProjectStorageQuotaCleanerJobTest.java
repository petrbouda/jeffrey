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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectStorageQuotaCleanerJobDescriptor;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectStorageQuotaCleanerJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final long MB = 1024L * 1024L;
    private static final long BUDGET = 100 * MB;

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    RepositoryStorage.Factory storageFactory;

    @Mock
    RepositoryStorage storage;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    private ProjectStorageQuotaCleanerJob job;

    @BeforeEach
    void setUp() {
        ProjectInfo projectInfo = new ProjectInfo(
                "proj-1", null, "my-project", null, null, "ws-1", NOW, NOW, java.util.Map.of(), null);

        when(projectManager.info()).thenReturn(projectInfo);
        when(projectManager.repositoryManager()).thenReturn(repositoryManager);
        when(storageFactory.apply(any())).thenReturn(storage);

        job = new ProjectStorageQuotaCleanerJob(
                workspacesManager,
                storageFactory,
                new ProjectStorageQuotaCleanerJobDescriptor(BUDGET),
                Duration.ofMinutes(15));
    }

    private void execute() {
        job.executeOnRepository(
                projectManager, storage, new ProjectStorageQuotaCleanerJobDescriptor(BUDGET), JobContext.EMPTY);
    }

    private static RepositoryFile recording(String id, Instant createdAt, long size, RecordingStatus status) {
        return new RepositoryFile(
                id, id, createdAt, size, SupportedRecordingFile.JFR, status, null);
    }

    private static RecordingSession finishedSession(
            String id, Instant createdAt, boolean retained, RepositoryFile... files) {

        return new RecordingSession(
                id, id, "inst-1", createdAt, createdAt.plusSeconds(60),
                RecordingStatus.FINISHED, null, null, List.of(files), retained);
    }

    private static RecordingSession activeSession(String id, Instant createdAt, RepositoryFile... files) {
        return new RecordingSession(
                id, id, "inst-1", createdAt, null,
                RecordingStatus.ACTIVE, null, null, List.of(files), false);
    }

    @Nested
    class UnderBudget {

        @Test
        void deletesNothing() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    finishedSession("s1", NOW.minusSeconds(3600), false,
                            recording("f1", NOW.minusSeconds(3600), 10 * MB, RecordingStatus.FINISHED))));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString());
            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }
    }

    @Nested
    class OverBudget {

        @Test
        void deletesOldestFinishedSessionsUntilUnderBudget() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    finishedSession("oldest", NOW.minusSeconds(9000), false,
                            recording("f1", NOW.minusSeconds(9000), 60 * MB, RecordingStatus.FINISHED)),
                    finishedSession("middle", NOW.minusSeconds(6000), false,
                            recording("f2", NOW.minusSeconds(6000), 60 * MB, RecordingStatus.FINISHED)),
                    finishedSession("newest", NOW.minusSeconds(3000), false,
                            recording("f3", NOW.minusSeconds(3000), 60 * MB, RecordingStatus.FINISHED))));

            execute();

            // 180MB total against a 100MB budget: dropping the two oldest brings it to 60MB
            ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
            verify(repositoryManager, org.mockito.Mockito.times(2))
                    .deleteRecordingSession(deleted.capture());

            assertEquals(List.of("oldest", "middle"), deleted.getAllValues());
        }

        @Test
        void stopsAsSoonAsItFitsTheBudget() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    finishedSession("oldest", NOW.minusSeconds(9000), false,
                            recording("f1", NOW.minusSeconds(9000), 80 * MB, RecordingStatus.FINISHED)),
                    finishedSession("newest", NOW.minusSeconds(3000), false,
                            recording("f2", NOW.minusSeconds(3000), 40 * MB, RecordingStatus.FINISHED))));

            execute();

            verify(repositoryManager).deleteRecordingSession("oldest");
            verify(repositoryManager, never()).deleteRecordingSession("newest");
        }

        @Test
        void neverDeletesRetainedSessions() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    finishedSession("pinned", NOW.minusSeconds(9000), true,
                            recording("f1", NOW.minusSeconds(9000), 90 * MB, RecordingStatus.FINISHED)),
                    finishedSession("normal", NOW.minusSeconds(3000), false,
                            recording("f2", NOW.minusSeconds(3000), 90 * MB, RecordingStatus.FINISHED))));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession("pinned");
            verify(repositoryManager).deleteRecordingSession("normal");
        }

        @Test
        void trimsActiveSessionOnlyAfterFinishedSessionsAreExhausted() {
            RepositoryFile oldChunk = recording("chunk-old", NOW.minusSeconds(2000), 70 * MB, RecordingStatus.FINISHED);
            RepositoryFile liveChunk = recording("chunk-live", NOW.minusSeconds(100), 60 * MB, RecordingStatus.ACTIVE);

            when(storage.listSessions(true)).thenReturn(List.of(
                    activeSession("live", NOW.minusSeconds(2500), oldChunk, liveChunk)));

            execute();

            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(storage).deleteRepositoryFiles(eq("live"), captor.capture());

            assertEquals(List.of("chunk-old"), captor.getValue(),
                    "The chunk the profiler is still writing must never be a candidate");
        }

        @Test
        void leavesLiveChunkAloneEvenWhenStillOverBudget() {
            RepositoryFile liveChunk = recording("chunk-live", NOW.minusSeconds(100), 500 * MB, RecordingStatus.ACTIVE);

            when(storage.listSessions(true)).thenReturn(List.of(
                    activeSession("live", NOW.minusSeconds(2500), liveChunk)));

            execute();

            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }

        @Test
        void doesNotTrimRetainedActiveSession() {
            RepositoryFile oldChunk = recording("chunk-old", NOW.minusSeconds(2000), 200 * MB, RecordingStatus.FINISHED);

            RecordingSession pinnedActive = new RecordingSession(
                    "live", "live", "inst-1", NOW.minusSeconds(2500), null,
                    RecordingStatus.ACTIVE, null, null, List.of(oldChunk), true);

            when(storage.listSessions(true)).thenReturn(List.of(pinnedActive));

            execute();

            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }
    }

    @Nested
    class DegenerateInput {

        @Test
        void toleratesFilesWithUnknownSize() {
            RepositoryFile unsized = new RepositoryFile(
                    "f1", "f1", NOW.minusSeconds(3600), null,
                    SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);

            when(storage.listSessions(true)).thenReturn(List.of(
                    finishedSession("s1", NOW.minusSeconds(3600), false, unsized)));

            // A file whose size could not be determined must count as zero, not blow up the sweep
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::run);
            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }

        private void run() {
            ProjectStorageQuotaCleanerJobTest.this.execute();
        }

        @Test
        void toleratesEmptyRepository() {
            when(storage.listSessions(true)).thenReturn(List.of());

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::run);
        }
    }

    @Nested
    class DescriptorValidation {

        @Test
        void rejectsNonPositiveBudget() {
            assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                            IllegalArgumentException.class,
                            () -> new ProjectStorageQuotaCleanerJobDescriptor(0))
                    .getMessage().contains("must be positive"));
        }
    }
}
