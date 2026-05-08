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

package cafe.jeffrey.server.core.workspace.consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.persistence.jdbc.JdbcServerPlatformRepositories;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DuckDBTest(migration = "classpath:db/migration/server")
@ExtendWith(MockitoExtension.class)
class DeleteSessionWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    private static WorkspaceEvent sessionDeletedEvent(String sessionId) {
        return new WorkspaceEvent(null, sessionId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED, Json.EMPTY,
                NOW, NOW, "test");
    }

    @Nested
    class HappyPath {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        RepositoryStorage.Factory repositoryStorageFactory;

        @Test
        void sessionDeleted_fromDatabaseAndRemoteStorage(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

            when(projectsManager.project(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(repositoryStorageFactory.apply(PROJECT_INFO)).thenReturn(repositoryStorage);

            // Verify session exists before deletion
            var repoRepository = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);
            List<ProjectInstanceSessionInfo> sessionsBefore = repoRepository.findUnfinishedSessions();
            assertTrue(sessionsBefore.stream().anyMatch(s -> s.sessionId().equals(SESSION_ID)));

            var consumer = new DeleteSessionWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory, FIXED_CLOCK);
            consumer.on(sessionDeletedEvent(SESSION_ID), projectsManager);

            // Verify session deleted from DB
            List<ProjectInstanceSessionInfo> sessionsAfter = repoRepository.findUnfinishedSessions();
            assertFalse(sessionsAfter.stream().anyMatch(s -> s.sessionId().equals(SESSION_ID)));

            // Verify remote storage deletion was called
            verify(repositoryStorage).deleteSession(SESSION_ID);
        }
    }

    @Nested
    class ProjectNotFound {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ServerPlatformRepositories platformRepositories;

        @Mock
        RepositoryStorage.Factory repositoryStorageFactory;

        @Test
        void projectNotFound_noOp() {
            when(projectsManager.project(ORIGIN_PROJECT_ID)).thenReturn(Optional.empty());

            var consumer = new DeleteSessionWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory, FIXED_CLOCK);

            assertDoesNotThrow(() ->
                    consumer.on(sessionDeletedEvent(SESSION_ID), projectsManager));

            verifyNoInteractions(repositoryStorageFactory);
        }
    }

    @Nested
    class InstanceExpiredTransition {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        RepositoryStorage.Factory repositoryStorageFactory;

        @Test
        void lastSessionDeleted_finishedInstanceBecomesExpired(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-finished-instance-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

            when(projectsManager.project(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(repositoryStorageFactory.apply(PROJECT_INFO)).thenReturn(repositoryStorage);

            // Verify instance is FINISHED before deletion
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.FINISHED, instanceRepo.find("inst-001").orElseThrow().status());

            var consumer = new DeleteSessionWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory, FIXED_CLOCK);
            consumer.on(sessionDeletedEvent(SESSION_ID), projectsManager);

            // Instance should now be EXPIRED with expiringAt and expiredAt set
            var instance = instanceRepo.find("inst-001").orElseThrow();
            assertEquals(ProjectInstanceStatus.EXPIRED, instance.status());
            assertNotNull(instance.expiringAt());
            assertNotNull(instance.expiredAt());
            assertEquals(NOW, instance.expiringAt());
            assertEquals(NOW, instance.expiredAt());
        }

        @Test
        void sessionDeleted_setsExpiringAt_butNotExpired_whenSessionsRemain(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

            when(projectsManager.project(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(repositoryStorageFactory.apply(PROJECT_INFO)).thenReturn(repositoryStorage);

            // Instance is ACTIVE with 2 sessions — deleting one should set expiringAt but not transition to EXPIRED
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.ACTIVE, instanceRepo.find("inst-001").orElseThrow().status());

            var consumer = new DeleteSessionWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory, FIXED_CLOCK);
            consumer.on(sessionDeletedEvent(SESSION_ID), projectsManager);

            // Instance should still be ACTIVE with expiringAt set
            var instance = instanceRepo.find("inst-001").orElseThrow();
            assertEquals(ProjectInstanceStatus.ACTIVE, instance.status());
            assertEquals(NOW, instance.expiringAt());
            assertNull(instance.expiredAt());
        }
    }

    @Nested
    class IsApplicable {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ServerPlatformRepositories platformRepositories;

        @Mock
        RepositoryStorage.Factory repositoryStorageFactory;

        @Test
        void onlyApplicable_forSessionDeletedEvents() {
            var consumer = new DeleteSessionWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory, FIXED_CLOCK);

            WorkspaceEvent sessionDeleted = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED, null, NOW, NOW, "test");
            WorkspaceEvent projectCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, null, NOW, NOW, "test");
            WorkspaceEvent sessionCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, null, NOW, NOW, "test");

            assertTrue(consumer.isApplicable(sessionDeleted));
            assertFalse(consumer.isApplicable(projectCreated));
            assertFalse(consumer.isApplicable(sessionCreated));
        }
    }
}
