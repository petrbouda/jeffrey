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

package pbouda.jeffrey.server.core.workspace.consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.project.ProjectsManager;
import pbouda.jeffrey.server.core.project.repository.RepositoryStorage;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.server.persistence.sql.JdbcServerPlatformRepositories;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
class DeleteProjectWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static WorkspaceEvent projectDeletedEvent() {
        return new WorkspaceEvent(null, ORIGIN_PROJECT_ID, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_DELETED, Json.EMPTY,
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
        void projectDeleted_cascadeCleanup(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

            when(projectsManager.project(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);

            // Mock remote storage - return sessions to delete
            RecordingSession session1 = new RecordingSession("session-001", "session-001", "inst-001",
                    NOW, null, null, null, null, List.of());
            RecordingSession session2 = new RecordingSession("session-002", "session-002", "inst-001",
                    NOW, null, null, null, null, List.of());
            when(repositoryStorageFactory.apply(PROJECT_INFO)).thenReturn(repositoryStorage);
            when(repositoryStorage.listSessions(false)).thenReturn(List.of(session1, session2));

            // Verify project exists before
            var projectRepo = platformRepositories.newProjectRepository(PROJECT_ID);
            assertTrue(projectRepo.find().isPresent());

            var consumer = new DeleteProjectWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory);
            consumer.on(projectDeletedEvent(), JOB_DESCRIPTOR, projectsManager);

            // 1. Remote sessions deleted
            verify(repositoryStorage).deleteSession("session-001");
            verify(repositoryStorage).deleteSession("session-002");

            // 2. SQL cascade delete - project no longer exists
            assertTrue(projectRepo.find().isEmpty());
        }
    }

    @Nested
    class RemoteStorageFailure {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        RepositoryStorage.Factory repositoryStorageFactory;

        @Test
        void remoteStorageFails_stillDeletesFromDatabase(DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);
            Path profilesDir = tempDir.resolve("profiles");

            when(projectsManager.project(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);

            // Remote storage throws exception
            when(repositoryStorageFactory.apply(PROJECT_INFO)).thenThrow(new RuntimeException("Storage unavailable"));

            var consumer = new DeleteProjectWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory);
            consumer.on(projectDeletedEvent(), JOB_DESCRIPTOR, projectsManager);

            // SQL cascade should still happen despite remote failure
            var projectRepo = platformRepositories.newProjectRepository(PROJECT_ID);
            assertTrue(projectRepo.find().isEmpty());
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
            var consumer = new DeleteProjectWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory);

            assertDoesNotThrow(() ->
                    consumer.on(projectDeletedEvent(), JOB_DESCRIPTOR, projectsManager));

            verifyNoInteractions(repositoryStorageFactory);
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
        void onlyApplicable_forProjectDeletedEvents() {
            var consumer = new DeleteProjectWorkspaceEventConsumer(
                    platformRepositories, repositoryStorageFactory);

            WorkspaceEvent projectDeleted = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_DELETED, null, NOW, NOW, "test");
            WorkspaceEvent projectCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, null, NOW, NOW, "test");
            WorkspaceEvent sessionDeleted = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED, null, NOW, NOW, "test");

            assertTrue(consumer.isApplicable(projectDeleted));
            assertFalse(consumer.isApplicable(projectCreated));
            assertFalse(consumer.isApplicable(sessionDeleted));
        }
    }
}
