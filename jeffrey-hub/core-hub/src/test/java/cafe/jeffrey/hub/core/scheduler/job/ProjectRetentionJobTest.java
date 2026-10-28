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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pass as a whole: one listing per project, rules that see each other's deletions, the
 * storage overview measured from what is left, and stale instances.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Duration PERIOD = Duration.ofMinutes(5);
    private static final Duration INSTANCE_RETENTION = Duration.ofDays(14);
    private static final long MB = 1024L * 1024L;

    private static final WorkspaceInfo WORKSPACE =
            new WorkspaceInfo("ws-1", "production", NOW, 1);

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    @Mock
    RepositoryStorage storage;

    private StorageOverviewCache storageOverview;
    private ProjectRetentionJob job;

    @BeforeEach
    void setUp() {
        when(workspacesManager.findAll()).thenReturn(List.of(workspaceManager));
        when(workspaceManager.info()).thenReturn(WORKSPACE);
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);
        when(projectsManager.findAll()).thenReturn(List.of(projectManager));
        stubProject(projectManager, "prj-1", storage);
        when(repositoryManager.deleteRecordingSession(anyString())).thenReturn(SessionDeletion.SESSION_DELETED);

        storageOverview = new StorageOverviewCache(Clock.fixed(NOW, ZoneOffset.UTC));
        JobConfig config = new JobConfig(true, PERIOD, Map.of(
                "session-retention", "7d",
                "max-sessions", "10",
                "active-session-file-retention", "7d",
                "max-size", "100M",
                "instance-retention", INSTANCE_RETENTION.toDays() + "d"));
        job = new ProjectRetentionJob(workspacesManager, config, storageOverview, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void stubProject(ProjectManager project, String projectId, RepositoryStorage repositoryStorage) {
        when(project.info()).thenReturn(new ProjectInfo(projectId, projectId, "ws-1", Optional.of(NOW)));
        when(project.repositoryManager()).thenReturn(repositoryManager);
        when(project.repositoryStorage()).thenReturn(repositoryStorage);
    }

    /** A session a newer one in its instance has ended, with one recording from its start. */
    private static RecordingSession ended(String id, Duration age, long size) {
        Instant createdAt = NOW.minus(age);
        return new RecordingSession(id, id, "inst-1", createdAt, false,
                List.of(new RepositoryFile(id + "-chunk", id + ".jfr", createdAt, size, true, null)));
    }

    @Nested
    class OneListing {

        @Test
        void listsEachProjectsSessionsOnceWithTheirFiles() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    ended("s-1", Duration.ofDays(1), 10 * MB)));

            job.execute();

            verify(storage, times(1)).listSessions(SessionDetail.WITH_FILES);
            verify(storage, never()).listSessions(SessionDetail.HEADERS);
        }

        /**
         * 170 MB against a 100 MB budget, of which the age window frees 80 MB first. The quota sees
         * the 90 MB that is left and deletes nothing more; a listing of its own taken before the
         * age window ran would have had it delete the next-oldest session too.
         */
        @Test
        void theQuotaCountsWhatTheAgeWindowAlreadyFreed() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    ended("newest", Duration.ofDays(1), 30 * MB),
                    ended("newer", Duration.ofDays(2), 60 * MB),
                    ended("aged-out", Duration.ofDays(8), 80 * MB)));

            job.execute();

            verify(repositoryManager).deleteRecordingSession("aged-out");
            verify(repositoryManager, never()).deleteRecordingSession("newer");
            verify(repositoryManager, never()).deleteRecordingSession("newest");
        }
    }

    @Nested
    class StorageOverviewFromThePass {

        @Test
        void publishesWhatRemainsOnceTheRulesRan() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    ended("newest", Duration.ofDays(1), 30 * MB),
                    ended("aged-out", Duration.ofDays(8), 80 * MB)));

            job.execute();

            var cached = storageOverview.get();
            assertEquals(NOW, cached.computedAt());
            ProjectStorage project = cached.overview().projects().getFirst();
            assertEquals("production", project.workspaceName());
            assertEquals("prj-1", project.projectId());
            assertEquals(30 * MB, project.totalSizeBytes());
            assertEquals(1, project.totalFiles());
        }

        @Test
        void aFailingProjectDoesNotStopTheOthers() {
            ProjectManager broken = mock(ProjectManager.class);
            RepositoryStorage brokenStorage = mock(RepositoryStorage.class);
            stubProject(broken, "broken", brokenStorage);
            when(brokenStorage.listSessions(SessionDetail.WITH_FILES)).thenThrow(new IllegalStateException("volume"));
            when(projectsManager.findAll()).thenReturn(List.of(broken, projectManager));
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    ended("s-1", Duration.ofDays(1), 10 * MB)));

            job.execute();

            List<ProjectStorage> projects = storageOverview.get().overview().projects();
            assertEquals(List.of("prj-1"), projects.stream().map(ProjectStorage::projectId).toList());
        }
    }

    @Nested
    class StaleInstances {

        private static final Instant LONG_AGO = NOW.minus(INSTANCE_RETENTION).minusSeconds(1);

        private static ProjectInstanceInfo instance(String id, Instant startedAt, int sessions) {
            return new ProjectInstanceInfo(id, Optional.of(startedAt), sessions);
        }

        @BeforeEach
        void noSessions() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of());
        }

        @Test
        void deletesAnInstanceThatNeverStreamedASession() {
            when(storage.instances()).thenReturn(List.of(
                    instance("abandoned", LONG_AGO, 0),
                    instance("young", NOW.minusSeconds(60), 0),
                    instance("with-sessions", LONG_AGO, 2)));

            job.execute();

            verify(storage).deleteInstanceDirectory("abandoned");
            verify(storage, never()).deleteInstanceDirectory("young");
            verify(storage, never()).deleteInstanceDirectory("with-sessions");
        }

        @Test
        void aDirectoryThatCannotBeDeletedDoesNotStopTheNextInstance() {
            when(storage.instances()).thenReturn(List.of(
                    instance("first", LONG_AGO, 0),
                    instance("second", LONG_AGO, 0)));
            doThrow(new IllegalStateException("busy")).when(storage).deleteInstanceDirectory("first");

            job.execute();

            verify(storage).deleteInstanceDirectory("second");
        }
    }

    @Nested
    class ManualRun {

        @Test
        void describesWhatThePassDeletedAndWhatIsLeft() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    ended("newest", Duration.ofDays(1), 30 * MB),
                    ended("aged-out", Duration.ofDays(8), 80 * MB)));

            String summary = job.runManually();

            assertTrue(summary.startsWith("Deleted 1 session, 0 files and 0 instances; "), summary);
            assertTrue(summary.endsWith(" across 1 project"), summary);
        }

        @Test
        void reportsItsPeriodAndType() {
            assertEquals(PERIOD, job.period());
            assertEquals(JobType.PROJECT_RETENTION, job.jobType());
        }
    }
}
