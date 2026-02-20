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

package pbouda.jeffrey.platform.workspace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/platform")
class WorkspaceEventQueueIntegrationTest {

    private static final Instant FIXED_TIME = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private static final String SCOPE_ID = "ws-scope-1";
    private static final String CONSUMER_ID = "consumer-1";

    private static DuckDBPersistentQueue<WorkspaceEvent> createQueue(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new DuckDBPersistentQueue<>(provider, "workspace-events", new WorkspaceEventSerializer(), FIXED_CLOCK);
    }

    private static WorkspaceEvent event(String originId, String projectId, WorkspaceEventType type) {
        return new WorkspaceEvent(null, originId, projectId, "ws-001", type, null,
                Instant.parse("2025-06-15T10:00:00Z"), Instant.parse("2025-06-15T10:00:01Z"), "test");
    }

    private static WorkspaceEvent eventWithContent(String originId, String projectId, WorkspaceEventType type, String content) {
        return new WorkspaceEvent(null, originId, projectId, "ws-001", type, content,
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
                    () -> assertEquals(original.workspaceId(), polled.workspaceId()),
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
            WorkspaceEvent third = event("evt-003", "proj-001", WorkspaceEventType.RECORDING_FILE_CREATED);
            queue.append(SCOPE_ID, third);

            // Second poll should only return the new event
            List<QueueEntry<WorkspaceEvent>> secondPoll = queue.poll(SCOPE_ID, CONSUMER_ID);

            assertAll(
                    () -> assertEquals(1, secondPoll.size()),
                    () -> assertEquals("evt-003", secondPoll.getFirst().payload().originEventId()),
                    () -> assertEquals(WorkspaceEventType.RECORDING_FILE_CREATED, secondPoll.getFirst().payload().eventType())
            );
        }
    }
}
