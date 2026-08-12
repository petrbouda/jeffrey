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

package cafe.jeffrey.hub.core.manager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cafe.jeffrey.hub.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.jdbc.JdbcHubPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Session deletion is executed directly (no event round-trip): one transaction covers the
 * database delete and the instance expiring/EXPIRED transitions, with the storage removal
 * last — still inside the transaction so a storage failure rolls everything back and the
 * next retention tick retries.
 */
@DuckDBTest(migration = "classpath:db/migration/server")
@ExtendWith(MockitoExtension.class)
class RepositoryManagerImplDeleteSessionIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final String INSTANCE_ID = "inst-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    @Mock
    RepositoryStorage repositoryStorage;

    private record Fixture(
            RepositoryManagerImpl manager,
            JdbcHubPlatformRepositories platformRepositories) {
    }

    private Fixture fixture(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        var platformRepositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);

        var manager = new RepositoryManagerImpl(
                FIXED_CLOCK,
                PROJECT_INFO,
                platformRepositories.newProjectRepositoryRepository(PROJECT_ID),
                platformRepositories.newProjectInstanceRepository(PROJECT_ID),
                repositoryStorage,
                mock(InstanceEnvironmentParser.class),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        return new Fixture(manager, platformRepositories);
    }

    @Nested
    class HappyPath {

        @Test
        void deletesSessionFromDatabaseAndStorage(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource);

            var repoRepository = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            assertTrue(repoRepository.findSessionById(SESSION_ID).isPresent());

            fixture.manager().deleteRecordingSession(SESSION_ID);

            assertTrue(repoRepository.findSessionById(SESSION_ID).isEmpty());
            verify(repositoryStorage).deleteSession(SESSION_ID);
        }

        @Test
        void missingSession_isNoOp_withoutStorageCall(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource);

            assertDoesNotThrow(() ->
                    fixture.manager().deleteRecordingSession("unknown-session"));

            verify(repositoryStorage, never()).deleteSession(anyString());
        }
    }

    @Nested
    class InstanceExpiredTransition {

        @Test
        void lastSessionDeleted_finishedInstanceBecomesExpired(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-finished-instance-with-session.sql");
            Fixture fixture = fixture(dataSource);

            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.FINISHED, instanceRepo.find(INSTANCE_ID).orElseThrow().status());

            fixture.manager().deleteRecordingSession(SESSION_ID);

            var instance = instanceRepo.find(INSTANCE_ID).orElseThrow();
            assertEquals(ProjectInstanceStatus.EXPIRED, instance.status());
            assertEquals(NOW, instance.expiringAt());
            assertEquals(NOW, instance.expiredAt());
        }

        @Test
        void sessionDeleted_setsExpiringAt_butNotExpired_whenSessionsRemain(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource);

            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.ACTIVE, instanceRepo.find(INSTANCE_ID).orElseThrow().status());

            fixture.manager().deleteRecordingSession(SESSION_ID);

            var instance = instanceRepo.find(INSTANCE_ID).orElseThrow();
            assertEquals(ProjectInstanceStatus.ACTIVE, instance.status());
            assertEquals(NOW, instance.expiringAt());
            assertNull(instance.expiredAt());
        }

        @Test
        void deletingTheSameSessionTwice_isIdempotent(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-finished-instance-with-session.sql");
            Fixture fixture = fixture(dataSource);

            fixture.manager().deleteRecordingSession(SESSION_ID);
            // Deleting the same session again is a no-op: the row is gone, so the transition
            // cannot re-fire
            assertDoesNotThrow(() -> fixture.manager().deleteRecordingSession(SESSION_ID));

            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.EXPIRED, instanceRepo.find(INSTANCE_ID).orElseThrow().status());
        }
    }

    @Nested
    class TransactionalRollback {

        @Test
        void storageFailure_rollsBackTheRowAndTransitions(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-finished-instance-with-session.sql");
            Fixture fixture = fixture(dataSource);

            doThrow(new RuntimeException("Storage unavailable"))
                    .when(repositoryStorage).deleteSession(SESSION_ID);

            assertThrows(RuntimeException.class, () ->
                    fixture.manager().deleteRecordingSession(SESSION_ID));

            // The transaction rolled back: the session row and the instance status are
            // untouched, so the next retention tick retries the whole deletion
            var repoRepository = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            assertTrue(repoRepository.findSessionById(SESSION_ID).isPresent());

            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.FINISHED, instanceRepo.find(INSTANCE_ID).orElseThrow().status());
        }
    }
}
