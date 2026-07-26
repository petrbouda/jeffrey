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

package cafe.jeffrey.hub.core.workspace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.persistentqueue.DuckDBPersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.FileCategory;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.event.InstanceExpiredEventContent;
import cafe.jeffrey.shared.common.model.workspace.event.InstanceFinishedEventContent;
import cafe.jeffrey.shared.common.model.workspace.event.SessionFileCreatedEventContent;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/server")
class WorkspaceEventQueueIntegrationTest {

    private static final Instant FIXED_TIME = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private static final String SCOPE_ID = "ws-scope-1";
    private static final String CONSUMER_ID = "consumer-1";

    private static DuckDBPersistentQueue<WorkspaceEvent> createQueue(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new DuckDBPersistentQueue<>(provider, "workspace-events", new WorkspaceEventSerializer(), FIXED_CLOCK);
    }

    private static WorkspaceEvent event(String referenceId, String projectId, WorkspaceEventType type) {
        return new WorkspaceEvent(null, referenceId, projectId, "ws-001", type, null,
                Instant.parse("2025-06-15T10:00:00Z"), Instant.parse("2025-06-15T10:00:01Z"), "test");
    }

    private static WorkspaceEvent eventWithContent(String referenceId, String projectId, WorkspaceEventType type, String content) {
        return new WorkspaceEvent(null, referenceId, projectId, "ws-001", type, content,
                Instant.parse("2025-06-15T10:00:00Z"), Instant.parse("2025-06-15T10:00:01Z"), "test");
    }

    @Nested
    class EventSerialization {

        @Test
        void projectCreatedEvent_roundTripsCorrectly(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent original = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_CREATED);

            queue.append(SCOPE_ID, original);
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertEquals(1, entries.size());
            WorkspaceEvent polled = entries.getFirst().payload();

            assertAll(
                    () -> assertEquals(original.originEventId(), polled.originEventId()),
                    () -> assertEquals(original.projectId(), polled.projectId()),
                    () -> assertEquals(original.workspaceRefId(), polled.workspaceRefId()),
                    () -> assertEquals(original.eventType(), polled.eventType()),
                    () -> assertEquals(original.content(), polled.content()),
                    () -> assertEquals(original.originCreatedAt(), polled.originCreatedAt()),
                    () -> assertEquals(original.createdAt(), polled.createdAt()),
                    () -> assertEquals(original.createdBy(), polled.createdBy())
            );
        }

        @Test
        void sessionCreatedEvent_roundTripsCorrectly(DataSource dataSource) {
            var queue = createQueue(dataSource);
            String contentJson = "{\"sessionName\":\"session-1\",\"startedAt\":\"2025-06-15T10:00:00Z\"}";
            WorkspaceEvent original = eventWithContent(
                    "evt-002", "proj-001", WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, contentJson);

            queue.append(SCOPE_ID, original);
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertEquals(1, entries.size());
            WorkspaceEvent polled = entries.getFirst().payload();

            assertAll(
                    () -> assertEquals(original.originEventId(), polled.originEventId()),
                    () -> assertEquals(original.projectId(), polled.projectId()),
                    () -> assertEquals(original.eventType(), polled.eventType()),
                    () -> assertEquals(contentJson, polled.content()),
                    () -> assertEquals(original.createdBy(), polled.createdBy())
            );
        }

        @Test
        void sessionFinishedEvent_roundTripsCorrectly(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent original = event("evt-003", "proj-001", WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED);

            queue.append(SCOPE_ID, original);
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertEquals(1, entries.size());
            WorkspaceEvent polled = entries.getFirst().payload();

            assertAll(
                    () -> assertEquals(original.originEventId(), polled.originEventId()),
                    () -> assertEquals(original.projectId(), polled.projectId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, polled.eventType()),
                    () -> assertEquals(original.createdBy(), polled.createdBy())
            );
        }

        @Test
        void sessionFileCreatedEvent_roundTripsContentWithEnumsAndInstant(DataSource dataSource) {
            var queue = createQueue(dataSource);
            var content = new SessionFileCreatedEventContent(
                    "instance-1",
                    "session-1",
                    "profile-20250615-100000.jfr",
                    SupportedRecordingFile.JFR,
                    FileCategory.RECORDING,
                    4096L,
                    Instant.parse("2025-06-15T10:00:00Z"));
            WorkspaceEvent original = eventWithContent(
                    "proj-001/instance-1/session-1/profile-20250615-100000",
                    "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED,
                    Json.toString(content));

            queue.append(SCOPE_ID, original);
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertEquals(1, entries.size());
            WorkspaceEvent polled = entries.getFirst().payload();
            SessionFileCreatedEventContent polledContent =
                    Json.read(polled.content(), SessionFileCreatedEventContent.class);

            assertAll(
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED,
                            polled.eventType()),
                    () -> assertEquals(content, polledContent,
                            "Enums and Instant must survive the JSON round-trip intact"));
        }

        @Test
        void artifactFileCreatedEvent_roundTripsCorrectly(DataSource dataSource) {
            var queue = createQueue(dataSource);
            var content = new SessionFileCreatedEventContent(
                    "instance-1",
                    "session-1",
                    "heap.hprof",
                    SupportedRecordingFile.HEAP_DUMP,
                    FileCategory.ARTIFACT,
                    123L,
                    Instant.parse("2025-06-15T10:05:00Z"));
            WorkspaceEvent original = eventWithContent(
                    "proj-001/instance-1/session-1/heap.hprof",
                    "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED,
                    Json.toString(content));

            queue.append(SCOPE_ID, original);
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertEquals(content, Json.read(entries.getFirst().payload().content(),
                    SessionFileCreatedEventContent.class));
        }

        @Test
        void instanceLifecycleEvents_roundTripCorrectly(DataSource dataSource) {
            var queue = createQueue(dataSource);
            Instant transitionAt = Instant.parse("2025-06-15T11:00:00Z");
            var finished = new InstanceFinishedEventContent("instance-1", transitionAt);
            var expired = new InstanceExpiredEventContent("instance-1", transitionAt);

            queue.append(SCOPE_ID, eventWithContent("instance-1", "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_FINISHED, Json.toString(finished)));
            queue.append(SCOPE_ID, eventWithContent("instance-1", "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_EXPIRED, Json.toString(expired)));

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertAll(
                    () -> assertEquals(2, entries.size(),
                            "FINISHED and EXPIRED share an originEventId but differ by type, so both survive dedup"),
                    () -> assertEquals(finished, Json.read(
                            entries.getFirst().payload().content(), InstanceFinishedEventContent.class)),
                    () -> assertEquals(expired, Json.read(
                            entries.getLast().payload().content(), InstanceExpiredEventContent.class)));
        }
    }

    @Nested
    class FileEventDeduplication {

        private static WorkspaceEvent fileEvent(String fileId, String projectId, WorkspaceEventType type) {
            return eventWithContent(fileId, projectId, type, Json.toString(new SessionFileCreatedEventContent(
                    "instance-1", "session-1", "profile-20250615-100000.jfr",
                    SupportedRecordingFile.JFR, FileCategory.RECORDING, 4096L,
                    Instant.parse("2025-06-15T10:00:00Z"))));
        }

        @Test
        void reAnnouncingTheSameFile_isSwallowed(DataSource dataSource) {
            var queue = createQueue(dataSource);
            String fileId = "proj-001/instance-1/session-1/profile-20250615-100000";

            // The scanner is stateless and re-offers every candidate on every tick; the queue's
            // unique dedup index is what makes that a no-op instead of a duplicate event.
            queue.append(SCOPE_ID, fileEvent(fileId, "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));
            queue.append(SCOPE_ID, fileEvent(fileId, "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));

            assertEquals(1, queue.poll(SCOPE_ID, CONSUMER_ID).size());
        }

        @Test
        void compressedAndRawChunk_shareOneEvent(DataSource dataSource) {
            var queue = createQueue(dataSource);
            // RepositoryFile.id() has recording extensions stripped, so profile-X.jfr and
            // profile-X.jfr.lz4 produce the same id — compression must not look like a new file.
            String sharedId = "proj-001/instance-1/session-1/profile-20250615-100000";

            queue.append(SCOPE_ID, fileEvent(sharedId, "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));
            queue.append(SCOPE_ID, fileEvent(sharedId, "proj-001",
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));

            assertEquals(1, queue.poll(SCOPE_ID, CONSUMER_ID).size());
        }

        @Test
        void sameFileNameInDifferentSessions_notDeduplicated(DataSource dataSource) {
            var queue = createQueue(dataSource);

            queue.append(SCOPE_ID, fileEvent("proj-001/instance-1/session-1/profile-20250615-100000",
                    "proj-001", WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));
            queue.append(SCOPE_ID, fileEvent("proj-001/instance-1/session-2/profile-20250615-100000",
                    "proj-001", WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));

            assertEquals(2, queue.poll(SCOPE_ID, CONSUMER_ID).size(),
                    "The file id is workspace-relative, so identical names in two sessions stay distinct");
        }
    }

    @Nested
    class EventDeduplication {

        @Test
        void sameEvent_isDeduplicated(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent event = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_CREATED);

            queue.append(SCOPE_ID, event);
            queue.append(SCOPE_ID, event);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertEquals(1, entries.size());
        }

        @Test
        void differentEventTypes_forSameProject_notDeduplicated(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent created = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_CREATED);
            WorkspaceEvent deleted = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_DELETED);

            queue.append(SCOPE_ID, created);
            queue.append(SCOPE_ID, deleted);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertAll(
                    () -> assertEquals(2, entries.size()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_CREATED, entries.get(0).payload().eventType()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_DELETED, entries.get(1).payload().eventType())
            );
        }

        @Test
        void sameEventType_differentProjects_notDeduplicated(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent eventProj1 = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_CREATED);
            WorkspaceEvent eventProj2 = event("evt-001", "proj-002", WorkspaceEventType.PROJECT_CREATED);

            queue.append(SCOPE_ID, eventProj1);
            queue.append(SCOPE_ID, eventProj2);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertAll(
                    () -> assertEquals(2, entries.size()),
                    () -> assertEquals("proj-001", entries.get(0).payload().projectId()),
                    () -> assertEquals("proj-002", entries.get(1).payload().projectId())
            );
        }
    }

    @Nested
    class EventOrdering {

        @Test
        void eventsPolledInInsertionOrder(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent first = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_CREATED);
            WorkspaceEvent second = event("evt-002", "proj-001", WorkspaceEventType.PROJECT_INSTANCE_CREATED);
            WorkspaceEvent third = event("evt-003", "proj-001", WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED);

            queue.append(SCOPE_ID, first);
            queue.append(SCOPE_ID, second);
            queue.append(SCOPE_ID, third);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertAll(
                    () -> assertEquals(3, entries.size()),
                    () -> assertEquals("evt-001", entries.get(0).payload().originEventId()),
                    () -> assertEquals("evt-002", entries.get(1).payload().originEventId()),
                    () -> assertEquals("evt-003", entries.get(2).payload().originEventId()),
                    () -> assertTrue(entries.get(0).offset() < entries.get(1).offset()),
                    () -> assertTrue(entries.get(1).offset() < entries.get(2).offset())
            );
        }
    }

    @Nested
    class AcknowledgeAndRepoll {

        @Test
        void afterAcknowledge_onlyNewEventsReturned(DataSource dataSource) {
            var queue = createQueue(dataSource);
            WorkspaceEvent first = event("evt-001", "proj-001", WorkspaceEventType.PROJECT_CREATED);
            WorkspaceEvent second = event("evt-002", "proj-001", WorkspaceEventType.PROJECT_INSTANCE_CREATED);

            queue.append(SCOPE_ID, first);
            queue.append(SCOPE_ID, second);

            // Poll and acknowledge up to the last event
            List<QueueEntry<WorkspaceEvent>> firstPoll = queue.poll(SCOPE_ID, CONSUMER_ID);
            long lastOffset = firstPoll.getLast().offset();
            queue.acknowledge(SCOPE_ID, CONSUMER_ID, lastOffset);

            // Append a new event after acknowledgement
            WorkspaceEvent third = event("evt-003", "proj-001", WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED);
            queue.append(SCOPE_ID, third);

            // Second poll should only return the new event
            List<QueueEntry<WorkspaceEvent>> secondPoll = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertAll(
                    () -> assertEquals(1, secondPoll.size()),
                    () -> assertEquals("evt-003", secondPoll.getFirst().payload().originEventId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, secondPoll.getFirst().payload().eventType())
            );
        }
    }
}
