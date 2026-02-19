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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;
import pbouda.jeffrey.platform.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.platform.workspace.model.InstanceCreatedEventContent;
import pbouda.jeffrey.platform.workspace.model.ProjectCreatedEventContent;
import pbouda.jeffrey.platform.workspace.model.SessionCreatedEventContent;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import pbouda.jeffrey.shared.common.model.workspace.*;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link WorkspaceEventsReplicatorJob}.
 *
 * <p>Tests call {@code executeOnWorkspace()} directly to exercise the core replication logic
 * (event conversion, queue batching, deduplication, migration callback, error handling)
 * with a real {@link DuckDBPersistentQueue} and mocked workspace/repository layers.
 *
 * <p>{@code WorkspacesManager} is a sealed interface that cannot be mocked. Since the workspace
 * iteration logic lives in the parent {@link WorkspaceJob}, tests bypass {@code execute()} and
 * invoke the protected {@code executeOnWorkspace()} method directly.
 */
@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class WorkspaceEventsReplicatorJobIntegrationTest {

    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String WORKSPACE_ID = "ws-001";
    private static final String CONSUMER_ID = "test-consumer";

    private static final WorkspaceEventsReplicatorJobDescriptor JOB_DESCRIPTOR =
            new WorkspaceEventsReplicatorJobDescriptor();

    @TempDir
    Path workspacePath;

    @Mock
    WorkspaceManager workspaceManager;
    @Mock
    RemoteWorkspaceRepository remoteWorkspaceRepository;
    @Mock
    SchedulerTrigger migrationCallback;

    private static RemoteProject createProject(String id, String name) {
        return new RemoteProject(
                id, name, "Label " + name, WORKSPACE_ID, 1718452800000L,
                "/workspaces", "ws-001", "ws-001/" + name,
                RepositoryType.ASYNC_PROFILER, Map.of("env", "test"));
    }

    private static RemoteProjectInstance createInstance(String instanceId, String projectId) {
        return new RemoteProjectInstance(
                instanceId, projectId, WORKSPACE_ID, 1718452900000L,
                "ws-001/project/" + instanceId);
    }

    private static RemoteProjectInstanceSession createSession(
            String sessionId, String projectId, String instanceId) {
        return new RemoteProjectInstanceSession(
                sessionId, projectId, WORKSPACE_ID, instanceId,
                1718453000000L, 1,
                "ws-001/project/" + instanceId + "/" + sessionId,
                "cpu=10ms", true);
    }

    private WorkspaceInfo createWorkspaceInfo(String wsId) {
        return new WorkspaceInfo(
                wsId, "origin-" + wsId, "repo-" + wsId, "Workspace " + wsId,
                "Test workspace", WorkspaceLocation.of(workspacePath), WorkspaceLocation.of(workspacePath),
                NOW, WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);
    }

    private static DuckDBPersistentQueue<WorkspaceEvent> createQueue(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new DuckDBPersistentQueue<>(provider, "workspace-events", new WorkspaceEventSerializer(), FIXED_CLOCK);
    }

    private void configureWorkspace() {
        WorkspaceInfo wsInfo = createWorkspaceInfo(WORKSPACE_ID);
        when(workspaceManager.resolveInfo()).thenReturn(wsInfo);
        when(workspaceManager.remoteWorkspaceRepository()).thenReturn(remoteWorkspaceRepository);
        lenient().when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));
    }

    private WorkspaceEventsReplicatorJob createJob(DuckDBPersistentQueue<WorkspaceEvent> queue) {
        return new WorkspaceEventsReplicatorJob(
                null, null, new JobDescriptorFactory(),
                Duration.ofMinutes(5), FIXED_CLOCK, queue, migrationCallback);
    }

    private void executeOnWorkspace(WorkspaceEventsReplicatorJob job, WorkspaceManager wm) {
        job.executeOnWorkspace(wm, JOB_DESCRIPTOR, JobContext.EMPTY);
    }

    @Nested
    class ProjectReplication {

        @Test
        void replicatesAllProjects_toQueue(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project1 = createProject("proj-001", "project-alpha");
            RemoteProject project2 = createProject("proj-002", "project-beta");
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project1, project2));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<QueueEntry<WorkspaceEvent>> entries = queue.findAll(WORKSPACE_ID);
            assertEquals(2, entries.size());

            List<String> projectIds = entries.stream()
                    .map(e -> e.payload().projectId()).sorted().toList();
            assertEquals(List.of("proj-001", "proj-002"), projectIds);

            entries.forEach(entry -> {
                WorkspaceEvent event = entry.payload();
                assertEquals(WorkspaceEventType.PROJECT_CREATED, event.eventType());
                assertEquals(WORKSPACE_ID, event.workspaceId());
                assertEquals(WorkspaceEventCreator.WORKSPACE_EVENTS_REPLICATOR_JOB.name(), event.createdBy());
            });
        }

        @Test
        void projectEvent_hasCorrectContent(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<QueueEntry<WorkspaceEvent>> entries = queue.findAll(WORKSPACE_ID);
            assertEquals(1, entries.size());

            WorkspaceEvent event = entries.getFirst().payload();
            assertNotNull(event.content());

            ProjectCreatedEventContent content = Json.read(event.content(), ProjectCreatedEventContent.class);
            assertAll(
                    () -> assertEquals("project-alpha", content.projectName()),
                    () -> assertEquals("Label project-alpha", content.projectLabel()),
                    () -> assertEquals("/workspaces", content.workspacesPath()),
                    () -> assertEquals("ws-001", content.relativeWorkspacePath()),
                    () -> assertEquals("ws-001/project-alpha", content.relativeProjectPath()),
                    () -> assertEquals(RepositoryType.ASYNC_PROFILER, content.repositoryType()),
                    () -> assertEquals(Map.of("env", "test"), content.attributes())
            );
        }

        @Test
        void projectEvent_hasCorrectTimestamps(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<QueueEntry<WorkspaceEvent>> entries = queue.findAll(WORKSPACE_ID);
            WorkspaceEvent event = entries.getFirst().payload();

            assertAll(
                    () -> assertEquals(Instant.ofEpochMilli(1718452800000L), event.originCreatedAt()),
                    () -> assertEquals(NOW, event.createdAt())
            );
        }
    }

    @Nested
    class InstanceReplication {

        @Test
        void replicatesAllInstances_forEachProject(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project1 = createProject("proj-001", "project-alpha");
            RemoteProject project2 = createProject("proj-002", "project-beta");
            RemoteProjectInstance instance1 = createInstance("inst-001", "proj-001");
            RemoteProjectInstance instance2 = createInstance("inst-002", "proj-002");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project1, project2));
            when(remoteWorkspaceRepository.allInstances(project1)).thenReturn(List.of(instance1));
            when(remoteWorkspaceRepository.allInstances(project2)).thenReturn(List.of(instance2));
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<WorkspaceEvent> instanceEvents = queue.findAll(WORKSPACE_ID).stream()
                    .map(QueueEntry::payload)
                    .filter(e -> e.eventType() == WorkspaceEventType.PROJECT_INSTANCE_CREATED)
                    .toList();

            assertEquals(2, instanceEvents.size());

            List<String> originIds = instanceEvents.stream()
                    .map(WorkspaceEvent::originEventId).sorted().toList();
            assertEquals(List.of("inst-001", "inst-002"), originIds);
        }

        @Test
        void instanceEvent_hasCorrectContent(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(project)).thenReturn(List.of(instance));
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<WorkspaceEvent> instanceEvents = queue.findAll(WORKSPACE_ID).stream()
                    .map(QueueEntry::payload)
                    .filter(e -> e.eventType() == WorkspaceEventType.PROJECT_INSTANCE_CREATED)
                    .toList();

            assertEquals(1, instanceEvents.size());
            InstanceCreatedEventContent content = Json.read(
                    instanceEvents.getFirst().content(), InstanceCreatedEventContent.class);
            assertEquals("ws-001/project/inst-001", content.relativeInstancePath());
        }

        @Test
        void instanceEvent_linksToCorrectProject(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(project)).thenReturn(List.of(instance));
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<WorkspaceEvent> instanceEvents = queue.findAll(WORKSPACE_ID).stream()
                    .map(QueueEntry::payload)
                    .filter(e -> e.eventType() == WorkspaceEventType.PROJECT_INSTANCE_CREATED)
                    .toList();

            assertEquals("proj-001", instanceEvents.getFirst().projectId());
        }
    }

    @Nested
    class SessionReplication {

        @Test
        void replicatesAllSessions_forEachProject(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");
            RemoteProjectInstanceSession session1 = createSession("sess-001", "proj-001", "inst-001");
            RemoteProjectInstanceSession session2 = createSession("sess-002", "proj-001", "inst-001");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(project)).thenReturn(List.of(instance));
            when(remoteWorkspaceRepository.allSessions(project)).thenReturn(List.of(session1, session2));

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<WorkspaceEvent> sessionEvents = queue.findAll(WORKSPACE_ID).stream()
                    .map(QueueEntry::payload)
                    .filter(e -> e.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED)
                    .toList();

            assertEquals(2, sessionEvents.size());

            List<String> sessionIds = sessionEvents.stream()
                    .map(WorkspaceEvent::originEventId).sorted().toList();
            assertEquals(List.of("sess-001", "sess-002"), sessionIds);
        }

        @Test
        void sessionEvent_hasCorrectContent(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");
            RemoteProjectInstanceSession session = createSession("sess-001", "proj-001", "inst-001");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(project)).thenReturn(List.of(instance));
            when(remoteWorkspaceRepository.allSessions(project)).thenReturn(List.of(session));

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<WorkspaceEvent> sessionEvents = queue.findAll(WORKSPACE_ID).stream()
                    .map(QueueEntry::payload)
                    .filter(e -> e.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED)
                    .toList();

            assertEquals(1, sessionEvents.size());
            SessionCreatedEventContent content = Json.read(
                    sessionEvents.getFirst().content(), SessionCreatedEventContent.class);

            assertAll(
                    () -> assertEquals("inst-001", content.instanceId()),
                    () -> assertEquals(1, content.order()),
                    () -> assertEquals("ws-001/project/inst-001/sess-001", content.relativeSessionPath()),
                    () -> assertEquals("cpu=10ms", content.profilerSettings()),
                    () -> assertTrue(content.streamingEnabled())
            );
        }
    }

    @Nested
    class InMemoryDeduplication {

        @Test
        void secondExecution_doesNotDuplicateEvents(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");
            RemoteProjectInstanceSession session = createSession("sess-001", "proj-001", "inst-001");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(project)).thenReturn(List.of(instance));
            when(remoteWorkspaceRepository.allSessions(project)).thenReturn(List.of(session));

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            // Execute twice with the same workspace
            executeOnWorkspace(job, workspaceManager);
            executeOnWorkspace(job, workspaceManager);

            List<QueueEntry<WorkspaceEvent>> entries = queue.findAll(WORKSPACE_ID);
            // Should be 3 events (1 project + 1 instance + 1 session), NOT 6
            assertEquals(3, entries.size());
        }

        @Test
        void newEntities_areReplicatedOnSubsequentExecution(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project1 = createProject("proj-001", "project-alpha");
            RemoteProject project2 = createProject("proj-002", "project-beta");

            // First execution: only project1
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project1));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            // Second execution: project1 + project2
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project1, project2));

            executeOnWorkspace(job, workspaceManager);

            List<WorkspaceEvent> projectEvents = queue.findAll(WORKSPACE_ID).stream()
                    .map(QueueEntry::payload)
                    .filter(e -> e.eventType() == WorkspaceEventType.PROJECT_CREATED)
                    .toList();

            // 2 total project events (proj-001 from first run, proj-002 from second run)
            assertEquals(2, projectEvents.size());

            List<String> projectIds = projectEvents.stream()
                    .map(WorkspaceEvent::projectId).sorted().toList();
            assertEquals(List.of("proj-001", "proj-002"), projectIds);
        }
    }

    @Nested
    class MigrationCallback {

        @Test
        void callbackExecuted_whenEventsProduced(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            verify(migrationCallback).execute();
        }

        @Test
        void callbackNotExecuted_whenNoEvents(DataSource dataSource) {
            configureWorkspace();
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            verify(migrationCallback, never()).execute();
        }

        @Test
        void callbackNotExecuted_whenNoNewEntities(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            // First execution: discovers project -> callback called
            executeOnWorkspace(job, workspaceManager);

            // Second execution: same project -> deduped -> no new events -> callback not called again
            executeOnWorkspace(job, workspaceManager);

            // Callback should have been called exactly once (from the first execution only)
            verify(migrationCallback, times(1)).execute();
        }
    }

    @Nested
    class MultiWorkspace {

        @Test
        void replicatesEvents_forEachWorkspace(DataSource dataSource) {
            WorkspaceManager workspaceManager2 = mock(WorkspaceManager.class);
            RemoteWorkspaceRepository remoteRepo2 = mock(RemoteWorkspaceRepository.class);

            String wsId2 = "ws-002";
            WorkspaceInfo wsInfo1 = createWorkspaceInfo(WORKSPACE_ID);
            WorkspaceInfo wsInfo2 = new WorkspaceInfo(
                    wsId2, "origin-" + wsId2, "repo-" + wsId2, "Workspace " + wsId2,
                    "Test workspace", WorkspaceLocation.of(workspacePath), WorkspaceLocation.of(workspacePath),
                    NOW, WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);

            when(workspaceManager.resolveInfo()).thenReturn(wsInfo1);
            when(workspaceManager.remoteWorkspaceRepository()).thenReturn(remoteWorkspaceRepository);
            when(workspaceManager2.resolveInfo()).thenReturn(wsInfo2);
            when(workspaceManager2.remoteWorkspaceRepository()).thenReturn(remoteRepo2);
            lenient().when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            RemoteProject project1 = createProject("proj-001", "project-alpha");
            RemoteProject project2 = createProject("proj-002", "project-beta");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project1));
            when(remoteWorkspaceRepository.allInstances(any())).thenReturn(List.of());
            when(remoteWorkspaceRepository.allSessions(any())).thenReturn(List.of());

            when(remoteRepo2.allProjects()).thenReturn(List.of(project2));
            when(remoteRepo2.allInstances(any())).thenReturn(List.of());
            when(remoteRepo2.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            // Simulate multi-workspace iteration: executeOnWorkspace for each workspace
            executeOnWorkspace(job, workspaceManager);
            executeOnWorkspace(job, workspaceManager2);

            // Events for workspace 1
            List<QueueEntry<WorkspaceEvent>> ws1Events = queue.findAll(WORKSPACE_ID);
            assertEquals(1, ws1Events.size());
            assertEquals("proj-001", ws1Events.getFirst().payload().projectId());
            assertEquals(WORKSPACE_ID, ws1Events.getFirst().payload().workspaceId());

            // Events for workspace 2
            List<QueueEntry<WorkspaceEvent>> ws2Events = queue.findAll(wsId2);
            assertEquals(1, ws2Events.size());
            assertEquals("proj-002", ws2Events.getFirst().payload().projectId());
            assertEquals(wsId2, ws2Events.getFirst().payload().workspaceId());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void continuesExecution_whenOneWorkspaceFails(DataSource dataSource) {
            WorkspaceManager workspaceManager2 = mock(WorkspaceManager.class);
            RemoteWorkspaceRepository failingRepo = mock(RemoteWorkspaceRepository.class);
            RemoteWorkspaceRepository workingRepo = mock(RemoteWorkspaceRepository.class);

            String wsId2 = "ws-002";
            WorkspaceInfo wsInfo1 = createWorkspaceInfo(WORKSPACE_ID);
            WorkspaceInfo wsInfo2 = new WorkspaceInfo(
                    wsId2, "origin-" + wsId2, "repo-" + wsId2, "Workspace " + wsId2,
                    "Test workspace", WorkspaceLocation.of(workspacePath), WorkspaceLocation.of(workspacePath),
                    NOW, WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);

            when(workspaceManager.resolveInfo()).thenReturn(wsInfo1);
            when(workspaceManager.remoteWorkspaceRepository()).thenReturn(failingRepo);
            when(workspaceManager2.resolveInfo()).thenReturn(wsInfo2);
            when(workspaceManager2.remoteWorkspaceRepository()).thenReturn(workingRepo);
            lenient().when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            // First workspace fails
            when(failingRepo.allProjects()).thenThrow(new RuntimeException("Filesystem error"));

            // Second workspace succeeds
            RemoteProject project = createProject("proj-001", "project-alpha");
            when(workingRepo.allProjects()).thenReturn(List.of(project));
            when(workingRepo.allInstances(any())).thenReturn(List.of());
            when(workingRepo.allSessions(any())).thenReturn(List.of());

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            // First workspace fails but exception is caught internally
            assertDoesNotThrow(() -> executeOnWorkspace(job, workspaceManager));

            // Second workspace still succeeds
            executeOnWorkspace(job, workspaceManager2);

            // Second workspace's events should be in the queue
            List<QueueEntry<WorkspaceEvent>> ws2Events = queue.findAll(wsId2);
            assertEquals(1, ws2Events.size());
            assertEquals("proj-001", ws2Events.getFirst().payload().projectId());
        }

        @Test
        void logsError_butDoesNotRethrow(DataSource dataSource) {
            configureWorkspace();
            when(remoteWorkspaceRepository.allProjects()).thenThrow(new RuntimeException("Connection failed"));

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            // Should not propagate the exception
            assertDoesNotThrow(() -> executeOnWorkspace(job, workspaceManager));

            List<QueueEntry<WorkspaceEvent>> entries = queue.findAll(WORKSPACE_ID);
            assertTrue(entries.isEmpty());
        }
    }

    @Nested
    class QueueDedupKey {

        @Test
        void eventsUseDedupKey_preventingQueueLevelDuplicates(DataSource dataSource) {
            var queue = createQueue(dataSource);

            // Create the same event and append it twice directly to the queue
            WorkspaceEvent event = new WorkspaceEvent(
                    null, "proj-001", "proj-001", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, "{}",
                    NOW, NOW, WorkspaceEventCreator.WORKSPACE_EVENTS_REPLICATOR_JOB.name());

            queue.appendBatch(WORKSPACE_ID, List.of(event));
            queue.appendBatch(WORKSPACE_ID, List.of(event));

            List<QueueEntry<WorkspaceEvent>> entries = queue.findAll(WORKSPACE_ID);
            assertEquals(1, entries.size());
        }
    }

    @Nested
    class EventOrdering {

        @Test
        void projectEvents_beforeInstances_beforeSessions(DataSource dataSource) {
            configureWorkspace();
            RemoteProject project = createProject("proj-001", "project-alpha");
            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");
            RemoteProjectInstanceSession session = createSession("sess-001", "proj-001", "inst-001");

            when(remoteWorkspaceRepository.allProjects()).thenReturn(List.of(project));
            when(remoteWorkspaceRepository.allInstances(project)).thenReturn(List.of(instance));
            when(remoteWorkspaceRepository.allSessions(project)).thenReturn(List.of(session));

            var queue = createQueue(dataSource);
            var job = createJob(queue);

            executeOnWorkspace(job, workspaceManager);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(WORKSPACE_ID, CONSUMER_ID);
            assertEquals(3, entries.size());

            assertEquals(WorkspaceEventType.PROJECT_CREATED, entries.get(0).payload().eventType());
            assertEquals(WorkspaceEventType.PROJECT_INSTANCE_CREATED, entries.get(1).payload().eventType());
            assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, entries.get(2).payload().eventType());

            // Offsets should be strictly increasing
            assertTrue(entries.get(0).offset() < entries.get(1).offset());
            assertTrue(entries.get(1).offset() < entries.get(2).offset());
        }
    }
}
