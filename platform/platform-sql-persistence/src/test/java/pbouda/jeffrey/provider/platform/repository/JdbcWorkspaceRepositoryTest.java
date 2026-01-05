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

package pbouda.jeffrey.provider.platform.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcWorkspaceRepositoryTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2025-01-15T12:00:00Z"), ZoneId.of("UTC"));

    @Nested
    class DeleteMethod {

        @Test
        void returnsTrue_whenWorkspaceExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            boolean result = repository.delete();

            assertTrue(result);
        }

        @Test
        void returnsFalse_whenWorkspaceNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("non-existent", provider, FIXED_CLOCK);

            boolean result = repository.delete();

            assertFalse(result);
        }
    }

    @Nested
    class FindAllProjectsMethod {

        @Test
        void returnsEmptyList_whenNoProjects(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            List<ProjectInfo> result = repository.findAllProjects();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsProjects_whenProjectsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-with-projects-and-events.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            List<ProjectInfo> result = repository.findAllProjects();

            assertEquals(2, result.size());
        }
    }

    @Nested
    class EventsMethod {

        @Test
        void returnsEmptyList_whenNoEvents(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            List<WorkspaceEvent> result = repository.findEvents();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEvents_whenEventsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-with-projects-and-events.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            List<WorkspaceEvent> result = repository.findEvents();

            assertEquals(2, result.size());
            assertEquals(WorkspaceEventType.PROJECT_CREATED, result.get(0).eventType());
        }

        @Test
        void returnsEventsFromOffset(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-with-projects-and-events.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            // Get events from offset 1 (should skip first event)
            List<WorkspaceEvent> result = repository.findEventsFromOffset(1000000000000001L);

            assertEquals(1, result.size());
            assertEquals("origin-event-002", result.get(0).originEventId());
        }
    }

    @Nested
    class BatchInsertEventsMethod {

        @Test
        void insertsEvents(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            List<WorkspaceEvent> events = List.of(
                    new WorkspaceEvent(0L, "origin-001", "proj-001", "ws-001", WorkspaceEventType.PROJECT_CREATED,
                            "{}", Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-01-01T10:00:01Z"), "test"),
                    new WorkspaceEvent(0L, "origin-002", "proj-001", "ws-001", WorkspaceEventType.SESSION_CREATED,
                            "{}", Instant.parse("2025-01-01T11:00:00Z"), Instant.parse("2025-01-01T11:00:01Z"), "test")
            );

            repository.batchInsertEvents(events);

            List<WorkspaceEvent> result = repository.findEvents();
            assertEquals(2, result.size());
        }

        @Test
        void handlesEmptyList(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            repository.batchInsertEvents(List.of());

            List<WorkspaceEvent> result = repository.findEvents();
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventConsumerMethod {

        @Test
        void createsAndFindsConsumer(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            repository.createEventConsumer("test-consumer");

            Optional<WorkspaceEventConsumer> result = repository.findEventConsumer("test-consumer");
            assertTrue(result.isPresent());
            assertEquals("test-consumer", result.get().consumerId());
        }

        @Test
        void returnsEmpty_whenConsumerNotExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            Optional<WorkspaceEventConsumer> result = repository.findEventConsumer("non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        void updatesConsumerOffset(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-with-event-consumer.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider, FIXED_CLOCK);

            repository.updateEventConsumerOffset("consumer-001", 200L);

            Optional<WorkspaceEventConsumer> result = repository.findEventConsumer("consumer-001");
            assertTrue(result.isPresent());
            assertEquals(Long.valueOf(200L), Long.valueOf(result.get().lastOffset()));
        }
    }
}
