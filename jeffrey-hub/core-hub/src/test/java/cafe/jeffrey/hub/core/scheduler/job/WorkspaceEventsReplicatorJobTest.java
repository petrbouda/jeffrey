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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.LiveWorkspacesManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.SchedulerTrigger;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.shared.common.model.workspace.CLIWorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import cafe.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.folderqueue.FolderQueue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceEventsReplicatorJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String WORKSPACE_ID = "ws-001";
    private static final String INTERNAL_WORKSPACE_ID = "ws-internal-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";

    @TempDir
    Path tempDir;

    private Path eventsDir() throws IOException {
        Path eventsDir = tempDir.resolve("workspaces").resolve(".events");
        Files.createDirectories(eventsDir);
        return eventsDir;
    }

    private HubJeffreyDirs jeffreyDirs() {
        return new HubJeffreyDirs(tempDir);
    }

    private void writeEventFile(Path eventsDir, String filename, CLIWorkspaceEvent event) throws IOException {
        Files.writeString(eventsDir.resolve(filename), Json.toString(event));
    }

    private static CLIWorkspaceEvent instanceCreatedEvent(String instanceId) {
        InstanceCreatedEventContent content = new InstanceCreatedEventContent("inst-dir-001");
        return new CLIWorkspaceEvent(instanceId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_CREATED, Json.toString(content),
                Instant.parse("2026-02-20T10:00:00Z"), "CLI");
    }

    private static CLIWorkspaceEvent projectCreatedEvent() {
        ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                "project-alpha", "Alpha", "/workspaces", "ws-001", "proj-001",
                RepositoryType.ASYNC_PROFILER, Map.of());
        return new CLIWorkspaceEvent(ORIGIN_PROJECT_ID, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, Json.toString(content),
                NOW.minusSeconds(3600), "CLI");
    }

    private static final String DEFAULT_REF_ID = "$default";
    private static final String DEFAULT_INTERNAL_WORKSPACE_ID = "ws-internal-default";

    private WorkspaceEventsReplicatorJob createJob(
            LiveWorkspacesManager workspacesManager,
            FolderQueue folderQueue,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            SchedulerTrigger migrationCallback) {

        return createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback, false);
    }

    private WorkspaceEventsReplicatorJob createJob(
            LiveWorkspacesManager workspacesManager,
            FolderQueue folderQueue,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            SchedulerTrigger migrationCallback,
            boolean autoCreateWorkspaces) {

        DefaultWorkspaceProperties properties = new DefaultWorkspaceProperties(DEFAULT_REF_ID, DEFAULT_REF_ID);
        return new WorkspaceEventsReplicatorJob(
                workspacesManager, Duration.ofMinutes(1), FIXED_CLOCK, folderQueue,
                workspaceEventQueue, migrationCallback, properties, autoCreateWorkspaces);
    }

    @Nested
    class ReplicatesEvents {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        WorkspaceManager workspaceManager;

        @Mock
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        @Mock
        SchedulerTrigger migrationCallback;

        private static final WorkspaceInfo WORKSPACE_INFO = new WorkspaceInfo(INTERNAL_WORKSPACE_ID, WORKSPACE_ID, WORKSPACE_ID, "Test Workspace", null, null, NOW, WorkspaceStatus.UNKNOWN, 0);

        @Test
        void instanceCreatedEvent_replicatedWithInternalWorkspaceId() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", instanceCreatedEvent("inst-new-001"));

            when(workspacesManager.findByReferenceId(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.localInfo()).thenReturn(WORKSPACE_INFO);
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            // Verify event was appended with the INTERNAL workspace ID, not the origin ID
            ArgumentCaptor<WorkspaceEvent> eventCaptor = ArgumentCaptor.forClass(WorkspaceEvent.class);
            verify(workspaceEventQueue).append(eq(INTERNAL_WORKSPACE_ID), eventCaptor.capture());
            WorkspaceEvent capturedEvent = eventCaptor.getValue();
            assertEquals(WorkspaceEventType.PROJECT_INSTANCE_CREATED, capturedEvent.eventType());
            assertEquals(WORKSPACE_ID, capturedEvent.workspaceRefId());

            // Verify file was acknowledged (moved to .processed)
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));

            // Verify migration callback was triggered
            verify(migrationCallback).execute();
        }

        @Test
        void projectCreatedEvent_replicatedWithInternalWorkspaceId() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", projectCreatedEvent());

            when(workspacesManager.findByReferenceId(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.localInfo()).thenReturn(WORKSPACE_INFO);
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            ArgumentCaptor<WorkspaceEvent> eventCaptor = ArgumentCaptor.forClass(WorkspaceEvent.class);
            verify(workspaceEventQueue).append(eq(INTERNAL_WORKSPACE_ID), eventCaptor.capture());
            assertEquals(WorkspaceEventType.PROJECT_CREATED, eventCaptor.getValue().eventType());

            // File acknowledged
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
        }
    }

    @Nested
    class DiscardsUnknownWorkspace {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        SchedulerTrigger migrationCallback;

        @Mock
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        @Test
        void unknownReferenceId_eventDiscarded() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", instanceCreatedEvent("inst-001"));

            when(workspacesManager.findByReferenceId(WORKSPACE_ID)).thenReturn(Optional.empty());

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verify(workspaceEventQueue, never()).append(any(), any());

            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));

            verify(migrationCallback, never()).execute();
        }
    }

    @Nested
    class AutoCreateWorkspace {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        SchedulerTrigger migrationCallback;

        @Mock
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        private static final String CREATED_INTERNAL_ID = "ws-internal-created";
        private static final WorkspaceInfo CREATED_WORKSPACE_INFO = new WorkspaceInfo(
                CREATED_INTERNAL_ID, WORKSPACE_ID, WORKSPACE_ID, WORKSPACE_ID,
                null, null, NOW, WorkspaceStatus.UNKNOWN, 0);

        @Test
        void enabled_unknownReferenceId_workspaceCreatedAndEventReplicated() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", instanceCreatedEvent("inst-001"));

            when(workspacesManager.findByReferenceId(WORKSPACE_ID)).thenReturn(Optional.empty());
            when(workspacesManager.create(any())).thenReturn(CREATED_WORKSPACE_INFO);
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback, true);

            job.execute(JobContext.EMPTY);

            ArgumentCaptor<WorkspacesManager.CreateWorkspaceRequest> requestCaptor =
                    ArgumentCaptor.forClass(WorkspacesManager.CreateWorkspaceRequest.class);
            verify(workspacesManager).create(requestCaptor.capture());
            assertEquals(WORKSPACE_ID, requestCaptor.getValue().referenceId());
            assertEquals(WORKSPACE_ID, requestCaptor.getValue().name());

            verify(workspaceEventQueue).append(eq(CREATED_INTERNAL_ID), any(WorkspaceEvent.class));
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));
            verify(migrationCallback).execute();
        }

        @Test
        void enabled_blankReferenceId_defaultWorkspaceMissing_neverAutoCreated() throws Exception {
            Path events = eventsDir();
            ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                    "project-x", "X", "/workspaces", "ws-x", "proj-x",
                    RepositoryType.ASYNC_PROFILER, Map.of());
            CLIWorkspaceEvent blankRefEvent = new CLIWorkspaceEvent(ORIGIN_PROJECT_ID, ORIGIN_PROJECT_ID, null,
                    WorkspaceEventType.PROJECT_CREATED, Json.toString(content), NOW.minusSeconds(60), "CLI");
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", blankRefEvent);

            when(workspacesManager.findByReferenceId(DEFAULT_REF_ID)).thenReturn(Optional.empty());

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback, true);

            job.execute(JobContext.EMPTY);

            verify(workspacesManager, never()).create(any());
            verify(workspaceEventQueue, never()).append(any(), any());
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));
        }

        @Test
        void enabled_createFails_eventNotAcknowledged_retriesNextTick() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", instanceCreatedEvent("inst-001"));

            when(workspacesManager.findByReferenceId(WORKSPACE_ID)).thenReturn(Optional.empty());
            when(workspacesManager.create(any())).thenThrow(new RuntimeException("duplicate reference_id"));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback, true);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            verify(workspaceEventQueue, never()).append(any(), any());
            assertTrue(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")),
                    "A failed create must leave the event file for the next tick");
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
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        @Test
        void malformedJsonFile_skippedWithoutException() throws Exception {
            Path events = eventsDir();
            Files.writeString(events.resolve("20260220120000100_aaaaaaaa.json"), "NOT VALID JSON {{{");

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            // File should still be in the events dir (skipped by parser)
            assertTrue(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));

            // No events replicated, no callback
            verify(workspaceEventQueue, never()).append(any(), any());
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
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        @Test
        void emptyEventsDirectory_completesWithoutErrors() throws Exception {
            eventsDir(); // create empty dir

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            verify(workspaceEventQueue, never()).append(any(), any());
            verify(migrationCallback, never()).execute();
        }

        @Test
        void nonExistentEventsDirectory_completesWithoutErrors() {
            // Don't create the events dir at all
            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            assertDoesNotThrow(() -> job.execute(JobContext.EMPTY));

            verify(workspaceEventQueue, never()).append(any(), any());
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
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        @Mock
        SchedulerTrigger migrationCallback;

        private static final WorkspaceInfo WORKSPACE_INFO = new WorkspaceInfo(INTERNAL_WORKSPACE_ID, WORKSPACE_ID, WORKSPACE_ID, "Test Workspace", null, null, NOW, WorkspaceStatus.UNKNOWN, 0);

        @Test
        void mixedWorkspaces_onlyKnownWorkspaceEventsReplicated() throws Exception {
            Path events = eventsDir();

            // First event: unknown workspace
            CLIWorkspaceEvent unknownWsEvent = new CLIWorkspaceEvent(
                    "inst-unknown", "proj-unknown", "ws-unknown",
                    WorkspaceEventType.PROJECT_INSTANCE_CREATED,
                    Json.toString(new InstanceCreatedEventContent("dir")),
                    NOW, "CLI");
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", unknownWsEvent);

            // Second event: known workspace
            writeEventFile(events, "20260220120000200_bbbbbbbb.json", instanceCreatedEvent("inst-new-001"));

            when(workspacesManager.findByReferenceId("ws-unknown")).thenReturn(Optional.empty());
            when(workspacesManager.findByReferenceId(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
            when(workspaceManager.localInfo()).thenReturn(WORKSPACE_INFO);
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            // Unknown workspace event was discarded (acknowledged but not appended)
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));

            // Known workspace event acknowledged
            assertFalse(Files.exists(events.resolve("20260220120000200_bbbbbbbb.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000200_bbbbbbbb.json")));

            // Only the known workspace event was appended with internal UUID
            verify(workspaceEventQueue).append(eq(INTERNAL_WORKSPACE_ID), any(WorkspaceEvent.class));
            verify(workspaceEventQueue, never()).append(eq("ws-unknown"), any());

            // Successful event was replicated, so callback triggered
            verify(migrationCallback).execute();
        }
    }

    @Nested
    class RoutesBlankRefIdToDefaultWorkspace {

        @Mock
        LiveWorkspacesManager workspacesManager;

        @Mock
        WorkspaceManager defaultWorkspaceManager;

        @Mock
        @SuppressWarnings("unchecked")
        PersistentQueue<WorkspaceEvent> workspaceEventQueue;

        @Mock
        SchedulerTrigger migrationCallback;

        private static final WorkspaceInfo DEFAULT_WORKSPACE_INFO = new WorkspaceInfo(
                DEFAULT_INTERNAL_WORKSPACE_ID, DEFAULT_REF_ID, DEFAULT_REF_ID, DEFAULT_REF_ID,
                null, null, NOW, WorkspaceStatus.UNKNOWN, 0);

        private static CLIWorkspaceEvent eventWithRefId(String refId) {
            ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                    "project-x", "X", "/workspaces", "ws-x", "proj-x",
                    RepositoryType.ASYNC_PROFILER, Map.of());
            return new CLIWorkspaceEvent(ORIGIN_PROJECT_ID, ORIGIN_PROJECT_ID, refId,
                    WorkspaceEventType.PROJECT_CREATED, Json.toString(content),
                    NOW.minusSeconds(60), "CLI");
        }

        @Test
        void nullWorkspaceRefId_routedToDefaultWorkspace() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", eventWithRefId(null));

            when(workspacesManager.findByReferenceId(DEFAULT_REF_ID))
                    .thenReturn(Optional.of(defaultWorkspaceManager));
            when(defaultWorkspaceManager.localInfo()).thenReturn(DEFAULT_WORKSPACE_INFO);
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            verify(workspaceEventQueue).append(eq(DEFAULT_INTERNAL_WORKSPACE_ID), any(WorkspaceEvent.class));
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));
            verify(migrationCallback).execute();
        }

        @Test
        void blankWorkspaceRefId_routedToDefaultWorkspace() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", eventWithRefId("   "));

            when(workspacesManager.findByReferenceId(DEFAULT_REF_ID))
                    .thenReturn(Optional.of(defaultWorkspaceManager));
            when(defaultWorkspaceManager.localInfo()).thenReturn(DEFAULT_WORKSPACE_INFO);
            when(migrationCallback.execute()).thenReturn(CompletableFuture.completedFuture(null));

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            verify(workspaceEventQueue).append(eq(DEFAULT_INTERNAL_WORKSPACE_ID), any(WorkspaceEvent.class));
        }

        @Test
        void nullRefId_butDefaultWorkspaceMissing_eventDiscarded() throws Exception {
            Path events = eventsDir();
            writeEventFile(events, "20260220120000100_aaaaaaaa.json", eventWithRefId(null));

            when(workspacesManager.findByReferenceId(DEFAULT_REF_ID)).thenReturn(Optional.empty());

            var folderQueue = new FolderQueue(jeffreyDirs().workspaces().resolve(".events"), FIXED_CLOCK);
            var job = createJob(workspacesManager, folderQueue, workspaceEventQueue, migrationCallback);

            job.execute(JobContext.EMPTY);

            verify(workspaceEventQueue, never()).append(any(), any());
            assertFalse(Files.exists(events.resolve("20260220120000100_aaaaaaaa.json")));
            assertTrue(Files.exists(events.resolve(".processed").resolve("20260220120000100_aaaaaaaa.json")));
        }
    }
}
