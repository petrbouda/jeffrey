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
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class InstanceLifecycleIntegrationTest {

    private static final String PROJECT_ID = "proj-001";

    @Nested
    class InstanceCreation {

        @Test
        void insertsInstance_withActiveStatus(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            var newInstance = new ProjectInstanceInfo(
                    "inst-new", PROJECT_ID, "new-host.example.com",
                    ProjectInstanceStatus.ACTIVE,
                    Instant.parse("2025-06-15T12:00:00Z"), null, 0, null);

            instanceRepo.insert(newInstance);

            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-new");
            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals("inst-new", found.get().id()),
                    () -> assertEquals(PROJECT_ID, found.get().projectId()),
                    () -> assertEquals("new-host.example.com", found.get().hostname()),
                    () -> assertEquals(ProjectInstanceStatus.ACTIVE, found.get().status()),
                    () -> assertNull(found.get().finishedAt())
            );
        }

        @Test
        void findAll_returnsAllForProject(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            List<ProjectInstanceInfo> instances = instanceRepo.findAll();
            assertEquals(2, instances.size());
        }
    }

    @Nested
    class InstanceFinishing {

        @Test
        void markFinished_setsStatusAndTimestamp(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            Instant finishedAt = Instant.parse("2025-06-15T15:00:00Z");
            instanceRepo.markFinished("inst-001", finishedAt);

            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-001");
            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(ProjectInstanceStatus.FINISHED, found.get().status()),
                    () -> assertEquals(finishedAt, found.get().finishedAt())
            );
        }
    }

    @Nested
    class InstanceReactivation {

        @Test
        void reactivate_setsStatusBackToActive(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // inst-002 is FINISHED in fixture
            instanceRepo.reactivate("inst-002");

            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-002");
            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(ProjectInstanceStatus.ACTIVE, found.get().status()),
                    () -> assertNull(found.get().finishedAt())
            );
        }
    }

    @Nested
    class SessionInteraction {

        @Test
        void sessionsLinkedToInstance(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            List<ProjectInstanceSessionInfo> sessions = instanceRepo.findSessions("inst-001");
            assertEquals(1, sessions.size());
            assertEquals("session-001", sessions.getFirst().sessionId());
        }

        @Test
        void findUnfinishedSessions_returnsOnlyUnfinished(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);
            var repoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            List<ProjectInstanceSessionInfo> unfinished = repoRepo.findUnfinishedSessions();
            assertEquals(1, unfinished.size());
            assertEquals("session-001", unfinished.getFirst().sessionId());
        }

        @Test
        void allSessionsFinished_instanceCanBeAutoFinished(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);
            var repoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // Finish the unfinished session
            Instant finishedAt = Instant.parse("2025-06-15T14:00:00Z");
            repoRepo.markSessionFinished("session-001", finishedAt);

            // Verify no unfinished sessions remain
            List<ProjectInstanceSessionInfo> unfinished = repoRepo.findUnfinishedSessions();
            assertTrue(unfinished.isEmpty());

            // Now the instance can be auto-finished
            instanceRepo.markFinished("inst-001", finishedAt);

            Optional<ProjectInstanceInfo> instance = instanceRepo.find("inst-001");
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceStatus.FINISHED, instance.get().status());
        }
    }
}
