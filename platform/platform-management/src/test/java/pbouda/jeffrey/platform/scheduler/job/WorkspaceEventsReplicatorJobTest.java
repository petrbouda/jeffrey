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

package pbouda.jeffrey.platform.scheduler.job;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.HeartbeatReplayReader;
import pbouda.jeffrey.provider.platform.JdbcPlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class WorkspaceEventsReplicatorJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String WORKSPACE_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2026-01-01T11:00:00Z"), null, Map.of());

    private static final RepositoryInfo REPO_INFO = new RepositoryInfo(
            "repo-001", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");

    @TempDir
    Path tempDir;

    private Path eventsDir() throws IOException {
        Path eventsDir = tempDir.resolve("workspaces").resolve(".events");
        Files.createDirectories(eventsDir);
        return eventsDir;
    }

    private JeffreyDirs jeffreyDirs() {
        return new JeffreyDirs(tempDir);
    }

    private void writeEventFile(Path eventsDir, String filename, WorkspaceEvent event) throws IOException {
        Files.writeString(eventsDir.resolve(filename), Json.toString(event));
    }

    private static WorkspaceEvent instanceCreatedEvent(String instanceId) {
        InstanceCreatedEventContent content = new InstanceCreatedEventContent("inst-dir-001");
        return new WorkspaceEvent(null, instanceId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_CREATED, Json.toString(content),
                Instant.parse("2026-02-20T10:00:00Z"), NOW, "CLI");
    }

    private static WorkspaceEvent projectCreatedEvent() {
        ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                "project-alpha", "Alpha", "/workspaces", "ws-001", "proj-001",
                RepositoryType.ASYNC_PROFILER, Map.of());
        return new WorkspaceEvent(null, ORIGIN_PROJECT_ID, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, Json.toString(content),
                NOW.minusSeconds(3600), NOW, "CLI");
    }

    @Nested
    class ProcessesEvents {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        SchedulerTrigger migrationCallback;

        @Test
        void instanceCreatedEvent_processedAndAcknowledged(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", instanceCreatedEvent("inst-new-001"));

            when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.projectInstanceRepository())
                    .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            job.execute(JobContext.EMPTY);

            // Verify instance was created in DB
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find("inst-new-001");
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceInfo.ProjectInstanceStatus.ACTIVE, instance.get().status());

            // Verify file was acknowledged (moved to .processed)
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));

            // Verify migration callback was triggered
            verify(migrationCallback).execute();
        }
    }

    @Nested
    class SkipsUnknownWorkspace {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        SchedulerTrigger migrationCallback;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void eventForUnknownWorkspace_remainsInQueue(DataSource dataSource) throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", instanceCreatedEvent("inst-001"));

            when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.empty());

            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            job.execute(JobContext.EMPTY);

            // File should still be in the events dir (not acknowledged)
            assertTrue(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertFalse(Files.isDirectory(events.resolve(".processed")));

            // Migration callback should NOT be triggered
            verify(migrationCallback, never()).execute();
        }
    }

    @Nested
    class HandlesMalformedFile {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        SchedulerTrigger migrationCallback;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void malformedJsonFile_skippedWithoutException(DataSource dataSource) throws Exception {
            Path events = eventsDir();
            Files.writeString(events.resolve("20260220120000100_aaaaaaaa.json"), "NOT VALID JSON {{{");

            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            // File should still be in the events dir (skipped by parser)
            assertTrue(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));

            // No events processed, no callback
            verify(migrationCallback, never()).execute();
        }
    }

    @Nested
    class NoEventsNoop {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        SchedulerTrigger migrationCallback;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void emptyEventsDirectory_completesWithoutErrors(DataSource dataSource) throws Exception {
            eventsDir(); // create empty dir

            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            verify(migrationCallback, never()).execute();
        }

        @Test
        void nonExistentEventsDirectory_completesWithoutErrors(DataSource dataSource) {
            // Don't create the events dir at all
            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            verify(migrationCallback, never()).execute();
        }
    }

    @Nested
    class AcknowledgesOnlySuccessful {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        SchedulerTrigger migrationCallback;

        @Test
        void mixedWorkspaces_onlyKnownWorkspaceEventsAcknowledged(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            Path events = eventsDir();

            // First event: unknown workspace
            WorkspaceEvent unknownWsEvent = new WorkspaceEvent(
                    null, "inst-unknown", "proj-unknown", "ws-unknown",
                    WorkspaceEventType.PROJECT_INSTANCE_CREATED,
                    Json.toString(new InstanceCreatedEventContent("dir")),
                    NOW, NOW, "CLI");
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", unknownWsEvent);

            // Second event: known workspace
            writeEventFile(events, "20260220120000200_bbbbbbbb.json", instanceCreatedEvent("inst-new-001"));

            when(workspacesManager.findById("ws-unknown")).thenReturn(Optional.empty());
            when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.projectInstanceRepository())
                    .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            job.execute(JobContext.EMPTY);

            // Unknown workspace event still in queue
            assertTrue(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));

            // Known workspace event acknowledged
            assertFalse(Files.exists(events.resolve("20260220120000200_bbbbbbbb.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000200_bbbbbbbb.json")));

            // Successful event was processed, so callback triggered
            verify(migrationCallback).execute();
        }
    }

    @Nested
    class RoutesToCorrectConsumer {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        SchedulerTrigger migrationCallback;

        @Test
        void projectCreatedEvent_routedToProjectConsumer(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", projectCreatedEvent());

            when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var job = new WorkspaceEventsReplicatorJob(
                    workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK,
                    platformRepositories, jeffreyDirs(), heartbeatReplayReader,
                    JOB_DESCRIPTOR, migrationCallback);

            job.execute(JobContext.EMPTY);

            // Verify the project consumer was invoked (existing project, repo already present → no creation)
            verify(projectsManager).findByOriginProjectId(ORIGIN_PROJECT_ID);
            verify(repositoryManager).info();

            // File acknowledged
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
        }
    }
}
