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

package cafe.jeffrey.hub.persistence.jdbc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.PendingWorkspaceEvent;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.WorkspaceEventQuery;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/server")
class JdbcWorkspaceEventLogRepositoryTest {

    private static final String WORKSPACE_ID = "ws-001";
    private static final String OTHER_WORKSPACE_ID = "ws-002";
    private static final Instant FIXED_TIME = Instant.parse("2026-08-12T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private static WorkspaceEventLogRepository repository(DataSource dataSource) {
        return new JdbcWorkspaceEventLogRepository(new DatabaseClientProvider(dataSource), FIXED_CLOCK);
    }

    private static WorkspaceEvent event(String originEventId, String projectId, WorkspaceEventType type) {
        return event(originEventId, projectId, type, FIXED_TIME);
    }

    private static WorkspaceEvent event(
            String originEventId, String projectId, WorkspaceEventType type, Instant createdAt) {
        return new WorkspaceEvent(
                null, originEventId, projectId, "ref-1", type, "{}", createdAt, createdAt, "TEST");
    }

    @Nested
    class AppendAndDedup {

        @Test
        void insertsRowAndReadsItBack(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);

            int inserted = repo.append(
                    WORKSPACE_ID,
                    event("session-1", "project-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED),
                    null);

            assertEquals(1, inserted);
            List<WorkspaceEvent> events = repo.findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 10));
            assertEquals(1, events.size());
            WorkspaceEvent read = events.getFirst();
            assertNotNull(read.eventId());
            assertEquals("session-1", read.originEventId());
            assertEquals("project-1", read.projectId());
            assertEquals("ref-1", read.workspaceRefId());
            assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED, read.eventType());
            assertEquals(FIXED_TIME, read.createdAt());
            assertEquals("TEST", read.createdBy());
        }

        @Test
        void multipleNullDedupKeysAllInsert(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            WorkspaceEvent sameFact = event("instance-1", "project-1", WorkspaceEventType.PROJECT_INSTANCE_FINISHED);

            assertEquals(1, repo.append(WORKSPACE_ID, sameFact, null));
            assertEquals(1, repo.append(WORKSPACE_ID, sameFact, null));
            assertEquals(1, repo.append(WORKSPACE_ID, sameFact, null));

            assertEquals(3, repo.count(WORKSPACE_ID));
        }

        @Test
        void duplicateDedupKeyIsDropped(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            WorkspaceEvent fileEvent = event(
                    "file-1", "project-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED);

            assertEquals(1, repo.append(WORKSPACE_ID, fileEvent, "project-1:file-1"));
            assertEquals(0, repo.append(WORKSPACE_ID, fileEvent, "project-1:file-1"));

            assertEquals(1, repo.count(WORKSPACE_ID));
        }

        @Test
        void sameDedupKeyInDifferentWorkspacesBothInsert(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            WorkspaceEvent fileEvent = event(
                    "file-1", "project-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED);

            assertEquals(1, repo.append(WORKSPACE_ID, fileEvent, "project-1:file-1"));
            assertEquals(1, repo.append(OTHER_WORKSPACE_ID, fileEvent, "project-1:file-1"));

            assertEquals(1, repo.count(WORKSPACE_ID));
            assertEquals(1, repo.count(OTHER_WORKSPACE_ID));
        }

        @Test
        void batchReportsOnlyActuallyInsertedRows(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            WorkspaceEvent first = event(
                    "file-1", "project-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED);
            WorkspaceEvent second = event(
                    "file-2", "project-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED);

            repo.append(WORKSPACE_ID, first, "key-1");

            int inserted = repo.appendBatch(WORKSPACE_ID, List.of(
                    PendingWorkspaceEvent.deduped(first, "key-1"),
                    PendingWorkspaceEvent.deduped(second, "key-2"),
                    PendingWorkspaceEvent.of(event(
                            "session-9", "project-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED))));

            assertEquals(2, inserted);
            assertEquals(3, repo.count(WORKSPACE_ID));
        }
    }

    @Nested
    class FindLatest {

        @Test
        void ordersByEventIdDescending(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            repo.append(WORKSPACE_ID, event("e-1", "p-1", WorkspaceEventType.PROJECT_CREATED), null);
            repo.append(WORKSPACE_ID, event("e-2", "p-1", WorkspaceEventType.PROJECT_INSTANCE_CREATED), null);
            repo.append(WORKSPACE_ID, event("e-3", "p-1", WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED), null);

            List<WorkspaceEvent> events = repo.findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 2));

            assertEquals(2, events.size());
            assertEquals("e-3", events.get(0).originEventId());
            assertEquals("e-2", events.get(1).originEventId());
            assertTrue(events.get(0).eventId() > events.get(1).eventId());
        }

        @Test
        void filtersByEventTypeInSql(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            repo.append(WORKSPACE_ID, event("e-1", "p-1", WorkspaceEventType.PROJECT_CREATED), null);
            repo.append(WORKSPACE_ID, event("e-2", "p-1", WorkspaceEventType.PROJECT_INSTANCE_CREATED), null);

            List<WorkspaceEvent> events = repo.findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, WorkspaceEventType.PROJECT_CREATED, Set.of(), 10));

            assertEquals(1, events.size());
            assertEquals("e-1", events.getFirst().originEventId());
        }

        @Test
        void filtersByProjectIdsInSql(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            repo.append(WORKSPACE_ID, event("e-1", "p-1", WorkspaceEventType.PROJECT_CREATED), null);
            repo.append(WORKSPACE_ID, event("e-2", "p-2", WorkspaceEventType.PROJECT_CREATED), null);
            repo.append(WORKSPACE_ID, event("e-3", "p-3", WorkspaceEventType.PROJECT_CREATED), null);

            List<WorkspaceEvent> events = repo.findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of("p-1", "p-3"), 10));

            assertEquals(2, events.size());
            assertEquals(Set.of("e-1", "e-3"),
                    Set.of(events.get(0).originEventId(), events.get(1).originEventId()));
        }

        @Test
        void isolatesWorkspaces(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            repo.append(WORKSPACE_ID, event("e-1", "p-1", WorkspaceEventType.PROJECT_CREATED), null);
            repo.append(OTHER_WORKSPACE_ID, event("e-2", "p-1", WorkspaceEventType.PROJECT_CREATED), null);

            List<WorkspaceEvent> events = repo.findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 10));

            assertEquals(1, events.size());
            assertEquals("e-1", events.getFirst().originEventId());
        }
    }

    @Nested
    class Retention {

        @Test
        void deletesOnlyRowsOlderThanCutoff(DataSource dataSource) {
            WorkspaceEventLogRepository repo = repository(dataSource);
            Instant old = FIXED_TIME.minusSeconds(3600);
            repo.append(WORKSPACE_ID, event("old", "p-1", WorkspaceEventType.PROJECT_CREATED, old), null);
            repo.append(WORKSPACE_ID, event("recent", "p-1", WorkspaceEventType.PROJECT_CREATED, FIXED_TIME), null);

            int deleted = repo.deleteOlderThan(FIXED_TIME.minusSeconds(600));

            assertEquals(1, deleted);
            List<WorkspaceEvent> events = repo.findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 10));
            assertEquals(1, events.size());
            assertEquals("recent", events.getFirst().originEventId());
        }
    }
}
