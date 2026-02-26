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
import pbouda.jeffrey.platform.streaming.SessionFinisher;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectRepositoryRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.MutableClock;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;



@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class CreateSessionWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String INSTANCE_ID = "inst-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private static final RepositoryInfo REPO_INFO = new RepositoryInfo(
            "repo-001", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final JeffreyDirs JEFFREY_DIRS = new JeffreyDirs(Path.of("/tmp/jeffrey-test"));

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    @Nested
    class CloseUnfinishedSessions {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Mock
        PlatformRepositories platformRepositories;

        @Mock
        SessionFinisher sessionFinisher;

        @Nested
        class MultipleUnfinishedSessions {

            @Test
            void allThreeSessions_forceFinishedWithCorrectFallbacks(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-multiple-unfinished-sessions.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                Instant newSessionOriginCreatedAt = Instant.parse("2025-06-15T11:00:00Z");
                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 4, "session-004", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-004", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        newSessionOriginCreatedAt, NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, sessionFinisher);
                consumer.on(event, JOB_DESCRIPTOR);

                var pathCaptor = ArgumentCaptor.forClass(Path.class);
                var fallbackCaptor = ArgumentCaptor.forClass(Instant.class);
                var sessionCaptor = ArgumentCaptor.forClass(ProjectInstanceSessionInfo.class);

                verify(sessionFinisher, times(3)).forceFinish(
                        any(ProjectRepositoryRepository.class),
                        eq(PROJECT_INFO),
                        sessionCaptor.capture(),
                        pathCaptor.capture(),
                        fallbackCaptor.capture());

                List<String> sessionIds = sessionCaptor.getAllValues().stream()
                        .map(ProjectInstanceSessionInfo::sessionId).toList();
                assertEquals(List.of("session-001", "session-002", "session-003"), sessionIds);

                List<Path> paths = pathCaptor.getAllValues();
                assertEquals(Path.of("/workspaces/ws-001/proj-001/session-001"), paths.get(0));
                assertEquals(Path.of("/workspaces/ws-001/proj-001/session-002"), paths.get(1));
                assertEquals(Path.of("/workspaces/ws-001/proj-001/session-003"), paths.get(2));

                // session-001 fallback = session-002.originCreatedAt
                // session-002 fallback = session-003.originCreatedAt
                // session-003 fallback = newSessionEvent.originCreatedAt
                List<Instant> fallbacks = fallbackCaptor.getAllValues();
                assertEquals(Instant.parse("2025-06-15T09:00:00Z"), fallbacks.get(0));
                assertEquals(Instant.parse("2025-06-15T10:00:00Z"), fallbacks.get(1));
                assertEquals(newSessionOriginCreatedAt, fallbacks.get(2));

                verify(repositoryManager).createSession(any());
            }
        }

        @Nested
        class SingleUnfinishedSession {

            @Test
            void singleSession_forceFinishedWithNewSessionTimestampAsFallback(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-single-unfinished-session.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                Instant newSessionOriginCreatedAt = Instant.parse("2025-06-15T09:00:00Z");
                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 2, "session-002", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-002", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        newSessionOriginCreatedAt, NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, sessionFinisher);
                consumer.on(event, JOB_DESCRIPTOR);

                verify(sessionFinisher).forceFinish(
                        any(ProjectRepositoryRepository.class),
                        eq(PROJECT_INFO),
                        argThat(s -> "session-001".equals(s.sessionId())),
                        eq(Path.of("/workspaces/ws-001/proj-001/session-001")),
                        eq(newSessionOriginCreatedAt));

                verify(repositoryManager).createSession(any());
            }
        }
    }
}
