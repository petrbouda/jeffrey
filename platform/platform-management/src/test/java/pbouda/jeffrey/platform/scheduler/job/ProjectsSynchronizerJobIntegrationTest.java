/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.HeartbeatReplayReader;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConsumerType;
import pbouda.jeffrey.platform.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;
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
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class ProjectsSynchronizerJobIntegrationTest {

    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String WORKSPACE_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static final JeffreyDirs JEFFREY_DIRS = new JeffreyDirs(Path.of("/tmp/jeffrey-test"));

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final RepositoryInfo REPO_INFO = new RepositoryInfo(
            "repo-001", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");

    private static DuckDBPersistentQueue<WorkspaceEvent> createQueue(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new DuckDBPersistentQueue<>(provider, "workspace-events", new WorkspaceEventSerializer(), FIXED_CLOCK);
    }

    private static WorkspaceEvent projectCreatedEvent(String originProjectId) {
        ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                "project-alpha", "Alpha", "/workspaces", "ws-001", "proj-001",
                RepositoryType.ASYNC_PROFILER, Map.of());
        return new WorkspaceEvent(null, originProjectId, originProjectId, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, Json.toString(content),
                NOW.minusSeconds(3600), NOW, "test");
    }

    private static WorkspaceEvent instanceCreatedEvent(String originProjectId, String instanceId) {
        InstanceCreatedEventContent content = new InstanceCreatedEventContent("inst-001");
        return new WorkspaceEvent(null, instanceId, originProjectId, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_CREATED, Json.toString(content),
                NOW.minusSeconds(1800), NOW, "test");
    }

    private static WorkspaceEvent sessionCreatedEvent(String originProjectId, String sessionId) {
        SessionCreatedEventContent content = new SessionCreatedEventContent(
                "inst-001", 1, "session-001", "cpu=true");
        return new WorkspaceEvent(null, sessionId, originProjectId, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, Json.toString(content),
                NOW.minusSeconds(900), NOW, "test");
    }

    @Nested
    class EmptyQueue {

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        RepositoryStorage.Factory remoteRepositoryStorageFactory;

        @Mock
        PlatformRepositories platformRepositories;

        @Mock
        ProjectsManager projectsManager;

        @Test
        void noEvents_noProcessingNoAcknowledgment(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceInfo wsInfo = new WorkspaceInfo(
                    WORKSPACE_ID, null, null, "Test Workspace", null, null, null,
                    Instant.parse("2025-01-01T10:00:00Z"), WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);
            when(workspaceManager.resolveInfo()).thenReturn(wsInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            var job = new ProjectsSynchronizerJob(
                    platformRepositories, remoteRepositoryStorageFactory, streamingConsumerManager,
                    queue, null, null, null, JEFFREY_DIRS, FIXED_CLOCK, heartbeatReplayReader,
                    Duration.ofMinutes(5));

            job.executeOnWorkspace(workspaceManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Queue should still be empty - no acknowledgment happened
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(
                    WORKSPACE_ID, WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER.name());
            assertTrue(entries.isEmpty());
        }
    }

    @Nested
    class SingleEventProcessing {

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        RepositoryStorage.Factory remoteRepositoryStorageFactory;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void instanceCreatedEvent_createsInstanceInDB(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            var queue = createQueue(dataSource);
            queue.append(WORKSPACE_ID, instanceCreatedEvent(ORIGIN_PROJECT_ID, "inst-new-001"));

            WorkspaceInfo wsInfo = new WorkspaceInfo(
                    WORKSPACE_ID, null, null, "Test Workspace", null, null, null,
                    Instant.parse("2025-01-01T10:00:00Z"), WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);
            when(workspaceManager.resolveInfo()).thenReturn(wsInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.projectInstanceRepository())
                    .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));

            var job = new ProjectsSynchronizerJob(
                    platformRepositories, remoteRepositoryStorageFactory, streamingConsumerManager,
                    queue, null, null, null, JEFFREY_DIRS, FIXED_CLOCK, heartbeatReplayReader,
                    Duration.ofMinutes(5));

            job.executeOnWorkspace(workspaceManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Verify instance was created in DB
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find("inst-new-001");
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceInfo.ProjectInstanceStatus.PENDING, instance.get().status());
            assertEquals(NOW.minusSeconds(1800), instance.get().startedAt());

            // Verify queue was acknowledged - re-poll returns empty
            List<QueueEntry<WorkspaceEvent>> remaining = queue.poll(
                    WORKSPACE_ID, WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER.name());
            assertTrue(remaining.isEmpty());
        }
    }

    @Nested
    class MultipleEventsProcessing {

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        RepositoryStorage.Factory remoteRepositoryStorageFactory;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void threeEvents_allProcessedAndAcknowledged(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            var queue = createQueue(dataSource);
            queue.append(WORKSPACE_ID, projectCreatedEvent(ORIGIN_PROJECT_ID));
            queue.append(WORKSPACE_ID, instanceCreatedEvent(ORIGIN_PROJECT_ID, "inst-001"));
            queue.append(WORKSPACE_ID, sessionCreatedEvent(ORIGIN_PROJECT_ID, "session-001"));

            WorkspaceInfo wsInfo = new WorkspaceInfo(
                    WORKSPACE_ID, null, null, "Test Workspace", null, null, null,
                    Instant.parse("2025-01-01T10:00:00Z"), WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);
            when(workspaceManager.resolveInfo()).thenReturn(wsInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            // PROJECT_CREATED: project already exists → skips creation, creates repo
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

            // INSTANCE_CREATED: creates instance
            when(projectManager.projectInstanceRepository())
                    .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));

            var job = new ProjectsSynchronizerJob(
                    platformRepositories, remoteRepositoryStorageFactory, streamingConsumerManager,
                    queue, null, null, null, JEFFREY_DIRS, FIXED_CLOCK, heartbeatReplayReader,
                    Duration.ofMinutes(5));

            job.executeOnWorkspace(workspaceManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Verify queue fully acknowledged
            List<QueueEntry<WorkspaceEvent>> remaining = queue.poll(
                    WORKSPACE_ID, WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER.name());
            assertTrue(remaining.isEmpty());
        }
    }

    @Nested
    class ErrorHandling {

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        RepositoryStorage.Factory remoteRepositoryStorageFactory;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void firstEventFails_secondEventStillProcessed(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            var queue = createQueue(dataSource);
            // First event: PROJECT_CREATED → will fail because findByOriginProjectId throws
            queue.append(WORKSPACE_ID, projectCreatedEvent("failing-proj"));
            // Second event: INSTANCE_CREATED → should still succeed
            queue.append(WORKSPACE_ID, instanceCreatedEvent(ORIGIN_PROJECT_ID, "inst-002"));

            WorkspaceInfo wsInfo = new WorkspaceInfo(
                    WORKSPACE_ID, null, null, "Test Workspace", null, null, null,
                    Instant.parse("2025-01-01T10:00:00Z"), WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);
            when(workspaceManager.resolveInfo()).thenReturn(wsInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);

            // First event triggers exception on PROJECT_CREATED consumer
            when(projectsManager.findByOriginProjectId("failing-proj"))
                    .thenThrow(new RuntimeException("Simulated DB error"));

            // Second event processes normally
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.projectInstanceRepository())
                    .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));

            var job = new ProjectsSynchronizerJob(
                    platformRepositories, remoteRepositoryStorageFactory, streamingConsumerManager,
                    queue, null, null, null, JEFFREY_DIRS, FIXED_CLOCK, heartbeatReplayReader,
                    Duration.ofMinutes(5));

            // Should NOT throw even though first event fails
            assertDoesNotThrow(() ->
                    job.executeOnWorkspace(workspaceManager, JOB_DESCRIPTOR, JobContext.EMPTY));

            // Verify second event was processed: instance created in DB
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find("inst-002");
            assertTrue(instance.isPresent());

            // Verify queue fully acknowledged (both events, including the failed one)
            List<QueueEntry<WorkspaceEvent>> remaining = queue.poll(
                    WORKSPACE_ID, WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER.name());
            assertTrue(remaining.isEmpty());
        }
    }

    @Nested
    class EventOrdering {

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Mock
        RepositoryStorage.Factory remoteRepositoryStorageFactory;

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void eventsProcessedInOffsetOrder(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            var queue = createQueue(dataSource);
            // Insert in order: project, instance-A, instance-B
            queue.append(WORKSPACE_ID, projectCreatedEvent(ORIGIN_PROJECT_ID));
            queue.append(WORKSPACE_ID, instanceCreatedEvent(ORIGIN_PROJECT_ID, "inst-A"));
            queue.append(WORKSPACE_ID, instanceCreatedEvent(ORIGIN_PROJECT_ID, "inst-B"));

            WorkspaceInfo wsInfo = new WorkspaceInfo(
                    WORKSPACE_ID, null, null, "Test Workspace", null, null, null,
                    Instant.parse("2025-01-01T10:00:00Z"), WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);
            when(workspaceManager.resolveInfo()).thenReturn(wsInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));
            when(projectManager.projectInstanceRepository())
                    .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));

            var job = new ProjectsSynchronizerJob(
                    platformRepositories, remoteRepositoryStorageFactory, streamingConsumerManager,
                    queue, null, null, null, JEFFREY_DIRS, FIXED_CLOCK, heartbeatReplayReader,
                    Duration.ofMinutes(5));

            job.executeOnWorkspace(workspaceManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Both instances should be created
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            assertTrue(instanceRepo.find("inst-A").isPresent());
            assertTrue(instanceRepo.find("inst-B").isPresent());

            // Queue fully consumed
            List<QueueEntry<WorkspaceEvent>> remaining = queue.poll(
                    WORKSPACE_ID, WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER.name());
            assertTrue(remaining.isEmpty());
        }
    }
}
