/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.persistence.jdbc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
class InstanceLifecycleIntegrationTest {

    private static final String PROJECT_ID = "proj-001";

    @Nested
    class InstanceCreation {

        @Test
        void insertsInstance_withPendingStatus(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            var newInstance = new ProjectInstanceInfo(
                    "inst-new", PROJECT_ID, "new-host.example.com",
                    ProjectInstanceStatus.PENDING,
                    Instant.parse("2025-06-15T12:00:00Z"), null, null, null, 0, null);

            instanceRepo.insert(newInstance);

            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-new");
            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals("inst-new", found.get().id()),
                    () -> assertEquals(PROJECT_ID, found.get().projectId()),
                    () -> assertEquals("new-host.example.com", found.get().instanceName()),
                    () -> assertEquals(ProjectInstanceStatus.PENDING, found.get().status()),
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
    class StoredStatus {

        @Test
        void instanceWithNoSessions_isPending(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // Insert an instance with no sessions
            var newInstance = new ProjectInstanceInfo(
                    "inst-no-sessions", PROJECT_ID, "no-sessions.example.com",
                    ProjectInstanceStatus.PENDING,
                    Instant.parse("2025-06-15T12:00:00Z"), null, null, null, 0, null);
            instanceRepo.insert(newInstance);

            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-no-sessions");
            assertTrue(found.isPresent());
            assertEquals(ProjectInstanceStatus.PENDING, found.get().status());
            assertNull(found.get().finishedAt());
            assertEquals(0, found.get().sessionCount());
        }

        @Test
        void instanceWithActiveStatus_isActive(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // inst-001 has status=ACTIVE in fixture
            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-001");
            assertTrue(found.isPresent());
            assertEquals(ProjectInstanceStatus.ACTIVE, found.get().status());
        }

        @Test
        void instanceWithFinishedStatus_isFinished(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // inst-002 has status=FINISHED in fixture
            Optional<ProjectInstanceInfo> found = instanceRepo.find("inst-002");
            assertTrue(found.isPresent());
            assertEquals(ProjectInstanceStatus.FINISHED, found.get().status());
        }

        @Test
        void updateStatus_changesStoredStatus(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // inst-001 starts as ACTIVE
            assertEquals(ProjectInstanceStatus.ACTIVE, instanceRepo.find("inst-001").get().status());

            // Update to FINISHED with finishedAt
            Instant finishedAt = Instant.parse("2025-06-15T14:00:00Z");
            instanceRepo.updateStatusAndFinishedAt("inst-001", ProjectInstanceStatus.FINISHED, finishedAt);

            Optional<ProjectInstanceInfo> instance = instanceRepo.find("inst-001");
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceStatus.FINISHED, instance.get().status());
            assertEquals(finishedAt, instance.get().finishedAt());
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void updateStatus_pendingToActive(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            // Insert a PENDING instance
            var newInstance = new ProjectInstanceInfo(
                    "inst-pending", PROJECT_ID, "pending.example.com",
                    ProjectInstanceStatus.PENDING,
                    Instant.parse("2025-06-15T12:00:00Z"), null, null, null, 0, null);
            instanceRepo.insert(newInstance);

            instanceRepo.updateStatus("inst-pending", ProjectInstanceStatus.ACTIVE);

            var found = instanceRepo.find("inst-pending").orElseThrow();
            assertEquals(ProjectInstanceStatus.ACTIVE, found.status());
        }

        @Test
        void updateStatusAndExpiredAt_setsStatusAndTimestamp(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            Instant expiredAt = Instant.parse("2025-06-20T10:00:00Z");
            instanceRepo.updateStatusAndExpiredAt("inst-002", ProjectInstanceStatus.EXPIRED, expiredAt);

            var found = instanceRepo.find("inst-002").orElseThrow();
            assertEquals(ProjectInstanceStatus.EXPIRED, found.status());
            assertEquals(expiredAt, found.expiredAt());
        }

        @Test
        void setExpiringAt_setsTimestamp(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            Instant expiringAt = Instant.parse("2025-06-18T08:00:00Z");
            instanceRepo.setExpiringAt("inst-001", expiringAt);

            var found = instanceRepo.find("inst-001").orElseThrow();
            assertEquals(expiringAt, found.expiringAt());
        }

        @Test
        void delete_removesInstance(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            assertEquals(2, instanceRepo.findAll().size());

            instanceRepo.delete("inst-002");

            assertEquals(1, instanceRepo.findAll().size());
            assertTrue(instanceRepo.find("inst-002").isEmpty());
        }

        @Test
        void findByStatus_filtersCorrectly(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            List<ProjectInstanceInfo> active = instanceRepo.findByStatus(ProjectInstanceStatus.ACTIVE);
            assertEquals(1, active.size());
            assertEquals("inst-001", active.getFirst().id());

            List<ProjectInstanceInfo> finished = instanceRepo.findByStatus(ProjectInstanceStatus.FINISHED);
            assertEquals(1, finished.size());
            assertEquals("inst-002", finished.getFirst().id());

            List<ProjectInstanceInfo> expired = instanceRepo.findByStatus(ProjectInstanceStatus.EXPIRED);
            assertTrue(expired.isEmpty());
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
    }

}
