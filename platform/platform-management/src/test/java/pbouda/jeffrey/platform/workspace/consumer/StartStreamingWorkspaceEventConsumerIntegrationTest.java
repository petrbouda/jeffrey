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

package pbouda.jeffrey.platform.workspace.consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.provider.platform.JdbcPlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class StartStreamingWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final String INSTANCE_ID = "inst-001";
    private static final String REPOSITORY_ID = "repo-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final RepositoryInfo REPOSITORY_INFO = new RepositoryInfo(
            REPOSITORY_ID, RepositoryType.ASYNC_PROFILER, null, "ws-001", "proj-001");

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static final String SESSION_CREATED_CONTENT = """
                {
                    "instanceId": "%s",
                    "order": 1,
                    "relativeSessionPath": "session-001",
                    "profilerSettings": "cpu=true"
                }
                """.formatted(INSTANCE_ID);

    private static WorkspaceEvent sessionCreatedEvent() {
        return new WorkspaceEvent(null, SESSION_ID, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, SESSION_CREATED_CONTENT,
                NOW, NOW, "test");
    }

    @Nested
    class HappyPath {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void sessionCreated_registersConsumer(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.of(REPOSITORY_INFO));

            var consumer = new StartStreamingWorkspaceEventConsumer(
                    projectsManager, streamingConsumerManager, platformRepositories);
            consumer.on(sessionCreatedEvent(), JOB_DESCRIPTOR);

            // Verify registerConsumer was called with correct arguments
            ArgumentCaptor<ProjectInstanceSessionInfo> sessionCaptor =
                    ArgumentCaptor.forClass(ProjectInstanceSessionInfo.class);
            verify(streamingConsumerManager).registerConsumer(
                    eq(REPOSITORY_INFO), sessionCaptor.capture(), any(), eq(PROJECT_INFO));

            ProjectInstanceSessionInfo captured = sessionCaptor.getValue();
            assertEquals(SESSION_ID, captured.sessionId());
            assertEquals(REPOSITORY_ID, captured.repositoryId());
            assertEquals(INSTANCE_ID, captured.instanceId());
            assertEquals(1, captured.order());
            assertEquals(Path.of("session-001"), captured.relativeSessionPath());
            assertEquals("cpu=true", captured.profilerSettings());
        }
    }

    @Nested
    class ProjectNotFound {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void projectNotFound_noOp() {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.empty());

            var consumer = new StartStreamingWorkspaceEventConsumer(
                    projectsManager, streamingConsumerManager, platformRepositories);

            assertDoesNotThrow(() ->
                    consumer.on(sessionCreatedEvent(), JOB_DESCRIPTOR));

            verifyNoInteractions(streamingConsumerManager);
        }
    }

    @Nested
    class RepositoryNotFound {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void repositoryNotFound_noOp() {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.empty());

            var consumer = new StartStreamingWorkspaceEventConsumer(
                    projectsManager, streamingConsumerManager, platformRepositories);

            assertDoesNotThrow(() ->
                    consumer.on(sessionCreatedEvent(), JOB_DESCRIPTOR));

            verifyNoInteractions(streamingConsumerManager);
        }
    }

    @Nested
    class IsApplicable {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void onlyApplicable_forSessionCreatedEvents() {
            var consumer = new StartStreamingWorkspaceEventConsumer(
                    projectsManager, streamingConsumerManager, platformRepositories);

            WorkspaceEvent sessionCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, null, NOW, NOW, "test");
            WorkspaceEvent sessionFinished = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, null, NOW, NOW, "test");
            WorkspaceEvent projectCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, null, NOW, NOW, "test");

            assertTrue(consumer.isApplicable(sessionCreated));
            assertFalse(consumer.isApplicable(sessionFinished));
            assertFalse(consumer.isApplicable(projectCreated));
        }
    }
}
