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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.HeartbeatReplayReader;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectRepositoryRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        HeartbeatReplayReader heartbeatReplayReader;

        @Nested
        class AllSessionsHaveHeartbeats {

            @Test
            void allThreeSessions_closedWithAccurateHeartbeatTimestamps(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-multiple-unfinished-sessions.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                Instant hb1 = Instant.parse("2025-06-15T08:30:00Z");
                Instant hb2 = Instant.parse("2025-06-15T09:45:00Z");
                Instant hb3 = Instant.parse("2025-06-15T10:15:00Z");

                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-001")), any()))
                        .thenReturn(Optional.of(hb1));
                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-002")), any()))
                        .thenReturn(Optional.of(hb2));
                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-003")), any()))
                        .thenReturn(Optional.of(hb3));

                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 4, "session-004", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-004", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        Instant.parse("2025-06-15T11:00:00Z"), NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, heartbeatReplayReader);
                consumer.on(event, JOB_DESCRIPTOR);

                var verifyRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                Optional<ProjectInstanceSessionInfo> session1 = verifyRepo.findSessionById("session-001");
                assertTrue(session1.isPresent());
                assertEquals(hb1, session1.get().finishedAt());
                assertEquals(hb1, session1.get().lastHeartbeatAt());

                Optional<ProjectInstanceSessionInfo> session2 = verifyRepo.findSessionById("session-002");
                assertTrue(session2.isPresent());
                assertEquals(hb2, session2.get().finishedAt());
                assertEquals(hb2, session2.get().lastHeartbeatAt());

                Optional<ProjectInstanceSessionInfo> session3 = verifyRepo.findSessionById("session-003");
                assertTrue(session3.isPresent());
                assertEquals(hb3, session3.get().finishedAt());
                assertEquals(hb3, session3.get().lastHeartbeatAt());

                verify(repositoryManager).createSession(any());
            }
        }

        @Nested
        class NoSessionsHaveHeartbeats {

            @Test
            void allThreeSessions_closedWithFallbackTimestamps(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-multiple-unfinished-sessions.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                when(heartbeatReplayReader.readLastHeartbeat(any(), any())).thenReturn(Optional.empty());

                Instant newSessionOriginCreatedAt = Instant.parse("2025-06-15T11:00:00Z");
                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 4, "session-004", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-004", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        newSessionOriginCreatedAt, NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, heartbeatReplayReader);
                consumer.on(event, JOB_DESCRIPTOR);

                var verifyRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                // session-001 fallback = session-002.originCreatedAt
                Optional<ProjectInstanceSessionInfo> session1 = verifyRepo.findSessionById("session-001");
                assertTrue(session1.isPresent());
                assertEquals(Instant.parse("2025-06-15T09:00:00Z"), session1.get().finishedAt());
                assertNull(session1.get().lastHeartbeatAt());

                // session-002 fallback = session-003.originCreatedAt
                Optional<ProjectInstanceSessionInfo> session2 = verifyRepo.findSessionById("session-002");
                assertTrue(session2.isPresent());
                assertEquals(Instant.parse("2025-06-15T10:00:00Z"), session2.get().finishedAt());
                assertNull(session2.get().lastHeartbeatAt());

                // session-003 fallback = newSessionEvent.originCreatedAt
                Optional<ProjectInstanceSessionInfo> session3 = verifyRepo.findSessionById("session-003");
                assertTrue(session3.isPresent());
                assertEquals(newSessionOriginCreatedAt, session3.get().finishedAt());
                assertNull(session3.get().lastHeartbeatAt());

                verify(repositoryManager).createSession(any());
            }
        }

        @Nested
        class MixedHeartbeatAvailability {

            @Test
            void mixOfHeartbeatsAndFallbacks(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-multiple-unfinished-sessions.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                Instant hb1 = Instant.parse("2025-06-15T08:30:00Z");
                Instant hb3 = Instant.parse("2025-06-15T10:15:00Z");

                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-001")), any()))
                        .thenReturn(Optional.of(hb1));
                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-002")), any()))
                        .thenReturn(Optional.empty());
                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-003")), any()))
                        .thenReturn(Optional.of(hb3));

                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 4, "session-004", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-004", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        Instant.parse("2025-06-15T11:00:00Z"), NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, heartbeatReplayReader);
                consumer.on(event, JOB_DESCRIPTOR);

                var verifyRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                // session-001: has heartbeat
                Optional<ProjectInstanceSessionInfo> session1 = verifyRepo.findSessionById("session-001");
                assertTrue(session1.isPresent());
                assertEquals(hb1, session1.get().finishedAt());
                assertEquals(hb1, session1.get().lastHeartbeatAt());

                // session-002: no heartbeat, fallback = session-003.originCreatedAt
                Optional<ProjectInstanceSessionInfo> session2 = verifyRepo.findSessionById("session-002");
                assertTrue(session2.isPresent());
                assertEquals(Instant.parse("2025-06-15T10:00:00Z"), session2.get().finishedAt());
                assertNull(session2.get().lastHeartbeatAt());

                // session-003: has heartbeat
                Optional<ProjectInstanceSessionInfo> session3 = verifyRepo.findSessionById("session-003");
                assertTrue(session3.isPresent());
                assertEquals(hb3, session3.get().finishedAt());
                assertEquals(hb3, session3.get().lastHeartbeatAt());

                verify(repositoryManager).createSession(any());
            }
        }

        @Nested
        class SingleUnfinishedSession {

            @Test
            void singleSession_closedWithHeartbeat(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-single-unfinished-session.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                Instant hb = Instant.parse("2025-06-15T08:45:00Z");
                when(heartbeatReplayReader.readLastHeartbeat(
                        eq(Path.of("/workspaces/ws-001/proj-001/session-001")), any()))
                        .thenReturn(Optional.of(hb));

                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 2, "session-002", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-002", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        Instant.parse("2025-06-15T09:00:00Z"), NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, heartbeatReplayReader);
                consumer.on(event, JOB_DESCRIPTOR);

                var verifyRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                Optional<ProjectInstanceSessionInfo> session1 = verifyRepo.findSessionById("session-001");
                assertTrue(session1.isPresent());
                assertEquals(hb, session1.get().finishedAt());
                assertEquals(hb, session1.get().lastHeartbeatAt());

                verify(repositoryManager).createSession(any());
            }

            @Test
            void singleSession_closedWithFallback_newSessionTimestamp(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-single-unfinished-session.sql");
                var provider = new DatabaseClientProvider(dataSource);
                var clock = new MutableClock(NOW);

                var realRepoRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(realRepoRepo);
                when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
                when(projectManager.info()).thenReturn(PROJECT_INFO);
                when(projectManager.repositoryManager()).thenReturn(repositoryManager);
                when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

                when(heartbeatReplayReader.readLastHeartbeat(any(), any())).thenReturn(Optional.empty());

                Instant newSessionOriginCreatedAt = Instant.parse("2025-06-15T09:00:00Z");
                SessionCreatedEventContent content = new SessionCreatedEventContent(
                        INSTANCE_ID, 2, "session-002", "cpu=true");
                WorkspaceEvent event = new WorkspaceEvent(
                        null, "session-002", ORIGIN_PROJECT_ID, WORKSPACE_ID,
                        WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED,
                        Json.toString(content),
                        newSessionOriginCreatedAt, NOW, "test");

                var consumer = new CreateSessionWorkspaceEventConsumer(
                        projectsManager, platformRepositories, JEFFREY_DIRS, heartbeatReplayReader);
                consumer.on(event, JOB_DESCRIPTOR);

                var verifyRepo = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

                // Single session fallback = newSessionEvent.originCreatedAt
                Optional<ProjectInstanceSessionInfo> session1 = verifyRepo.findSessionById("session-001");
                assertTrue(session1.isPresent());
                assertEquals(newSessionOriginCreatedAt, session1.get().finishedAt());
                assertNull(session1.get().lastHeartbeatAt());

                verify(repositoryManager).createSession(any());
            }
        }
    }
}
