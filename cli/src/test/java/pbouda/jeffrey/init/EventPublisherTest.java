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

package pbouda.jeffrey.init;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;
import pbouda.jeffrey.shared.folderqueue.FolderQueue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventPublisherTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-20T15:30:45.123Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private EventPublisher createPublisher() {
        Path queueDir = tempDir.resolve(".events");
        FolderQueue folderQueue = new FolderQueue(queueDir, FIXED_CLOCK);
        return new EventPublisher(folderQueue, FIXED_CLOCK);
    }

    private WorkspaceEvent readSingleEvent() throws IOException {
        Path queueDir = tempDir.resolve(".events");
        List<Path> files;
        try (var stream = Files.list(queueDir)) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        assertEquals(1, files.size());
        String content = Files.readString(files.getFirst());
        return Json.read(content, WorkspaceEvent.class);
    }

    @Nested
    class PublishProjectCreated {

        @Test
        void writesValidProjectCreatedEvent() throws IOException {
            var publisher = createPublisher();

            publisher.publishProjectCreated(
                    "proj-001", "ws-001", "my-project", "My Project",
                    "/workspaces", RepositoryType.ASYNC_PROFILER,
                    Map.of("env", "prod", "region", "us-east"));

            WorkspaceEvent event = readSingleEvent();

            assertAll(
                    () -> assertNull(event.eventId()),
                    () -> assertEquals("proj-001", event.originEventId()),
                    () -> assertEquals("proj-001", event.projectId()),
                    () -> assertEquals("ws-001", event.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_CREATED, event.eventType()),
                    () -> assertEquals(FIXED_INSTANT, event.originCreatedAt()),
                    () -> assertNull(event.createdAt()),
                    () -> assertEquals("CLI", event.createdBy())
            );

            ProjectCreatedEventContent content = Json.read(event.content(), ProjectCreatedEventContent.class);
            assertAll(
                    () -> assertEquals("my-project", content.projectName()),
                    () -> assertEquals("My Project", content.projectLabel()),
                    () -> assertEquals("/workspaces", content.workspacesPath()),
                    () -> assertEquals(RepositoryType.ASYNC_PROFILER, content.repositoryType()),
                    () -> assertEquals(Map.of("env", "prod", "region", "us-east"), content.attributes()),
                    () -> assertEquals("ws-001", content.relativeWorkspacePath()),
                    () -> assertEquals("my-project", content.relativeProjectPath())
            );
        }
    }

    @Nested
    class PublishInstanceCreated {

        @Test
        void writesValidInstanceCreatedEvent() throws IOException {
            var publisher = createPublisher();

            publisher.publishInstanceCreated("inst-001", "proj-001", "ws-001");

            WorkspaceEvent event = readSingleEvent();

            assertAll(
                    () -> assertEquals("inst-001", event.originEventId()),
                    () -> assertEquals("proj-001", event.projectId()),
                    () -> assertEquals("ws-001", event.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_CREATED, event.eventType()),
                    () -> assertEquals(FIXED_INSTANT, event.originCreatedAt()),
                    () -> assertEquals("CLI", event.createdBy())
            );

            InstanceCreatedEventContent content = Json.read(event.content(), InstanceCreatedEventContent.class);
            assertEquals("inst-001", content.relativeInstancePath());
        }
    }

    @Nested
    class PublishSessionCreated {

        @Test
        void writesValidSessionCreatedEvent() throws IOException {
            var publisher = createPublisher();

            publisher.publishSessionCreated(
                    "session-001", "proj-001", "ws-001",
                    "inst-001", 3, "cpu=true", true);

            WorkspaceEvent event = readSingleEvent();

            assertAll(
                    () -> assertEquals("session-001", event.originEventId()),
                    () -> assertEquals("proj-001", event.projectId()),
                    () -> assertEquals("ws-001", event.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, event.eventType()),
                    () -> assertEquals(FIXED_INSTANT, event.originCreatedAt()),
                    () -> assertEquals("CLI", event.createdBy())
            );

            SessionCreatedEventContent content = Json.read(event.content(), SessionCreatedEventContent.class);
            assertAll(
                    () -> assertEquals("inst-001", content.instanceId()),
                    () -> assertEquals(3, content.order()),
                    () -> assertEquals("inst-001/session-001", content.relativeSessionPath()),
                    () -> assertEquals("cpu=true", content.profilerSettings()),
                    () -> assertTrue(content.streamingEnabled())
            );
        }

        @Test
        void relativeSessionPathBuiltFromInstanceAndSessionIds() throws IOException {
            var publisher = createPublisher();

            publisher.publishSessionCreated(
                    "sess-xyz", "proj-001", "ws-001",
                    "host-abc", 1, null, false);

            WorkspaceEvent event = readSingleEvent();
            SessionCreatedEventContent content = Json.read(event.content(), SessionCreatedEventContent.class);

            assertEquals("host-abc/sess-xyz", content.relativeSessionPath());
            assertFalse(content.streamingEnabled());
        }
    }
}
