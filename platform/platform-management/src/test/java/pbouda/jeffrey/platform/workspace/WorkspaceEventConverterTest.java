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

package pbouda.jeffrey.platform.workspace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.workspace.model.RecordingFileCreatedEventContent;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.repository.*;
import pbouda.jeffrey.shared.common.model.workspace.*;
import pbouda.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceEventConverterTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final String WORKSPACE_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";

    private static final WorkspaceInfo WORKSPACE_INFO = new WorkspaceInfo(
            WORKSPACE_ID, null, null, "Test Workspace", null, null, null,
            Instant.parse("2026-01-01T10:00:00Z"), WorkspaceType.LIVE, WorkspaceStatus.AVAILABLE, 0);

    private static final WorkspaceEventCreator CREATOR = WorkspaceEventCreator.WORKSPACE_EVENTS_REPLICATOR_JOB;

    @Nested
    class FromQueueEntry {

        @Test
        void mapsQueueEntryFieldsCorrectly() {
            WorkspaceEvent payload = new WorkspaceEvent(
                    null, "origin-001", PROJECT_ID, WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, "{\"key\":\"value\"}",
                    Instant.parse("2026-02-20T10:00:00Z"), null, "test");

            Instant queueCreatedAt = Instant.parse("2026-02-20T11:00:00Z");
            QueueEntry<WorkspaceEvent> entry = new QueueEntry<>(42L, payload, queueCreatedAt);

            WorkspaceEvent result = WorkspaceEventConverter.fromQueueEntry(entry);

            assertAll(
                    () -> assertEquals(42L, result.eventId()),
                    () -> assertEquals("origin-001", result.originEventId()),
                    () -> assertEquals(PROJECT_ID, result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_CREATED, result.eventType()),
                    () -> assertEquals("{\"key\":\"value\"}", result.content()),
                    () -> assertEquals(Instant.parse("2026-02-20T10:00:00Z"), result.originCreatedAt()),
                    () -> assertEquals(queueCreatedAt, result.createdAt()),
                    () -> assertEquals("test", result.createdBy())
            );
        }
    }

    @Nested
    class ProjectCreated {

        @Test
        void createsEventWithCorrectFieldsAndContent() {
            long createdAtMillis = Instant.parse("2026-02-20T08:00:00Z").toEpochMilli();
            RemoteProject remoteProject = new RemoteProject(
                    "origin-proj-001", "my-project", "My Project",
                    WORKSPACE_ID, createdAtMillis, "/workspaces",
                    "ws-dir", "proj-dir",
                    RepositoryType.ASYNC_PROFILER, Map.of("env", "prod"));

            WorkspaceEvent result = WorkspaceEventConverter.projectCreated(
                    NOW, remoteProject, WORKSPACE_INFO, CREATOR);

            assertAll(
                    () -> assertNull(result.eventId()),
                    () -> assertEquals("origin-proj-001", result.originEventId()),
                    () -> assertEquals("origin-proj-001", result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_CREATED, result.eventType()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtMillis), result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt()),
                    () -> assertEquals(CREATOR.name(), result.createdBy())
            );

            ProjectCreatedEventContent content = Json.read(result.content(), ProjectCreatedEventContent.class);
            assertAll(
                    () -> assertEquals("my-project", content.projectName()),
                    () -> assertEquals("My Project", content.projectLabel()),
                    () -> assertEquals("/workspaces", content.workspacesPath()),
                    () -> assertEquals("ws-dir", content.relativeWorkspacePath()),
                    () -> assertEquals("proj-dir", content.relativeProjectPath()),
                    () -> assertEquals(RepositoryType.ASYNC_PROFILER, content.repositoryType()),
                    () -> assertEquals(Map.of("env", "prod"), content.attributes())
            );
        }
    }

    @Nested
    class InstanceCreated {

        @Test
        void createsEventWithCorrectTypeAndContent() {
            long createdAtMillis = Instant.parse("2026-02-20T09:00:00Z").toEpochMilli();
            RemoteProjectInstance remoteInstance = new RemoteProjectInstance(
                    "inst-001", "origin-proj-001", WORKSPACE_ID,
                    createdAtMillis, "inst-dir-001");

            WorkspaceEvent result = WorkspaceEventConverter.instanceCreated(
                    NOW, remoteInstance, WORKSPACE_INFO, CREATOR);

            assertAll(
                    () -> assertNull(result.eventId()),
                    () -> assertEquals("inst-001", result.originEventId()),
                    () -> assertEquals("origin-proj-001", result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_CREATED, result.eventType()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtMillis), result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt()),
                    () -> assertEquals(CREATOR.name(), result.createdBy())
            );

            InstanceCreatedEventContent content = Json.read(result.content(), InstanceCreatedEventContent.class);
            assertEquals("inst-dir-001", content.relativeInstancePath());
        }
    }

    @Nested
    class SessionCreated {

        @Test
        void createsEventWithCorrectTypeAndContent() {
            long createdAtMillis = Instant.parse("2026-02-20T10:00:00Z").toEpochMilli();
            RemoteProjectInstanceSession remoteSession = new RemoteProjectInstanceSession(
                    "session-001", "origin-proj-001", WORKSPACE_ID,
                    "inst-001", createdAtMillis, 3,
                    "inst-001/session-001", "cpu=true");

            WorkspaceEvent result = WorkspaceEventConverter.sessionCreated(
                    NOW, remoteSession, WORKSPACE_INFO, CREATOR);

            assertAll(
                    () -> assertNull(result.eventId()),
                    () -> assertEquals("session-001", result.originEventId()),
                    () -> assertEquals("origin-proj-001", result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, result.eventType()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtMillis), result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt()),
                    () -> assertEquals(CREATOR.name(), result.createdBy())
            );

            SessionCreatedEventContent content = Json.read(result.content(), SessionCreatedEventContent.class);
            assertAll(
                    () -> assertEquals("inst-001", content.instanceId()),
                    () -> assertEquals(3, content.order()),
                    () -> assertEquals("inst-001/session-001", content.relativeSessionPath()),
                    () -> assertEquals("cpu=true", content.profilerSettings())
            );
        }
    }

    @Nested
    class SessionDeleted {

        @Test
        void createsEventWithEmptyContentAndCorrectType() {
            WorkspaceEvent result = WorkspaceEventConverter.sessionDeleted(
                    NOW, WORKSPACE_ID, PROJECT_ID, "session-001", CREATOR);

            assertAll(
                    () -> assertNull(result.eventId()),
                    () -> assertEquals("session-001", result.originEventId()),
                    () -> assertEquals(PROJECT_ID, result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED, result.eventType()),
                    () -> assertEquals(Json.EMPTY, result.content()),
                    () -> assertEquals(NOW, result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt()),
                    () -> assertEquals(CREATOR.name(), result.createdBy())
            );
        }
    }

    @Nested
    class SessionFinished {

        @Test
        void createsEventWithEmptyContentAndCorrectType() {
            ProjectInfo projectInfo = new ProjectInfo(
                    PROJECT_ID, "origin-proj-001", "Test", "Label", null,
                    WORKSPACE_ID, WorkspaceType.LIVE, NOW, null, Map.of());

            ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                    "session-001", "repo-001", "inst-001", 1,
                    Path.of("inst-001/session-001"), "cpu=true",
                    NOW, NOW, null);

            WorkspaceEvent result = WorkspaceEventConverter.sessionFinished(
                    NOW, projectInfo, sessionInfo, CREATOR);

            assertAll(
                    () -> assertEquals("session-001", result.originEventId()),
                    () -> assertEquals(PROJECT_ID, result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, result.eventType()),
                    () -> assertEquals(Json.EMPTY, result.content()),
                    () -> assertEquals(NOW, result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt())
            );
        }
    }

    @Nested
    class ProjectDeleted {

        @Test
        void createsEventWithEmptyContentAndCorrectType() {
            WorkspaceEvent result = WorkspaceEventConverter.projectDeleted(
                    NOW, WORKSPACE_ID, PROJECT_ID, CREATOR);

            assertAll(
                    () -> assertEquals(PROJECT_ID, result.originEventId()),
                    () -> assertEquals(PROJECT_ID, result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_DELETED, result.eventType()),
                    () -> assertEquals(Json.EMPTY, result.content()),
                    () -> assertEquals(NOW, result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt())
            );
        }
    }

    @Nested
    class RecordingFileCreated {

        @TempDir
        Path recordingTempDir;

        @Test
        void createsEventWithRecordingFileContent() throws IOException {
            ProjectInfo projectInfo = new ProjectInfo(
                    PROJECT_ID, "origin-proj-001", "Test", "Label", null,
                    WORKSPACE_ID, WorkspaceType.LIVE, NOW, null, Map.of());

            Instant fileCreatedAt = Instant.parse("2026-02-20T10:00:00Z");
            RepositoryFile originalFile = new RepositoryFile(
                    "file-001", "recording.jfr", fileCreatedAt, 1024L,
                    SupportedRecordingFile.JFR, true, RecordingStatus.FINISHED,
                    Path.of("/workspaces/ws-001/proj-001/recording.jfr"));

            Path compressedPath = recordingTempDir.resolve("recording.jfr.lz4");
            Files.writeString(compressedPath, "compressed-content");

            WorkspaceEvent result = WorkspaceEventConverter.recordingFileCreated(
                    NOW, projectInfo, "session-001", originalFile,
                    compressedPath, 1024L, 512L, CREATOR);

            assertAll(
                    () -> assertEquals("session-001", result.originEventId()),
                    () -> assertEquals(PROJECT_ID, result.projectId()),
                    () -> assertEquals(WORKSPACE_ID, result.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.RECORDING_FILE_CREATED, result.eventType()),
                    () -> assertEquals(fileCreatedAt, result.originCreatedAt()),
                    () -> assertEquals(NOW, result.createdAt()),
                    () -> assertEquals(CREATOR.name(), result.createdBy())
            );

            RecordingFileCreatedEventContent content = Json.read(
                    result.content(), RecordingFileCreatedEventContent.class);
            assertAll(
                    () -> assertEquals(compressedPath.toString(), content.filePath()),
                    () -> assertEquals("recording.jfr.lz4", content.fileName()),
                    () -> assertEquals(1024L, content.originalSize()),
                    () -> assertEquals(512L, content.compressedSize()),
                    () -> assertEquals(fileCreatedAt, content.originalFileCreatedAt()),
                    () -> assertNotNull(content.originalFileModifiedAt())
            );
        }
    }
}
