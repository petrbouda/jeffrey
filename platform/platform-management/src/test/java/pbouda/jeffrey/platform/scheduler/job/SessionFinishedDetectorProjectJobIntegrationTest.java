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

package pbouda.jeffrey.platform.scheduler.job;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.SessionFinishedDetectorProjectJobDescriptor;
import pbouda.jeffrey.platform.streaming.SessionFinisher;
import pbouda.jeffrey.provider.platform.JdbcPlatformRepositories;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class SessionFinishedDetectorProjectJobIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String INSTANCE_ID = "inst-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofMinutes(5);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final SessionFinishedDetectorProjectJobDescriptor JOB_DESCRIPTOR =
            new SessionFinishedDetectorProjectJobDescriptor();

    /**
     * Creates a SessionFinishedDetectorProjectJob with null for parent-class dependencies
     * (WorkspacesManager, RepositoryStorage.Factory, JobDescriptorFactory) since we test
     * executeOnRepository directly and those are only used in the parent's execute() method.
     */
    private static SessionFinishedDetectorProjectJob createJob(
            JeffreyDirs jeffreyDirs,
            JdbcPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher) {

        return new SessionFinishedDetectorProjectJob(
                null, null, null,
                Duration.ofSeconds(30), HEARTBEAT_THRESHOLD, FIXED_CLOCK, jeffreyDirs,
                platformRepositories, sessionFinisher);
    }

    @Nested
    class SessionDetectedFinished {

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        SessionFinisher sessionFinisher;

        @Test
        void sessionFinished_instanceDerivesFinished_noActiveSessionsRemaining(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/detector/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);
            var jeffreyDirs = new JeffreyDirs(tempDir);

            // Create session directories on filesystem (NULL workspaces_path → jeffreyDirs.workspaces())
            Files.createDirectories(tempDir.resolve("workspaces/ws-001/proj-001/session-001"));
            Files.createDirectories(tempDir.resolve("workspaces/ws-001/proj-001/session-002"));

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            // SessionFinisher mock also marks sessions finished in DB (simulates real behavior)
            var repoRepository = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);
            doAnswer(invocation -> {
                ProjectInstanceSessionInfo session = invocation.getArgument(2);
                repoRepository.markSessionFinished(session.sessionId(), NOW);
                return true;
            }).when(sessionFinisher).tryFinishFromHeartbeat(
                    any(), eq(PROJECT_INFO), any(), any(), eq(HEARTBEAT_THRESHOLD), eq(NOW));

            var job = createJob(jeffreyDirs, platformRepositories, sessionFinisher);
            job.executeOnRepository(projectManager, repositoryStorage, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Instance status should derive to FINISHED since all sessions are now finished
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find(INSTANCE_ID);
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceStatus.FINISHED, instance.get().status());
        }
    }

    @Nested
    class SessionNotFinished {

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        SessionFinisher sessionFinisher;

        @Test
        void sessionNotFinished_instanceRemainsActive(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/detector/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);
            var jeffreyDirs = new JeffreyDirs(tempDir);

            Files.createDirectories(tempDir.resolve("workspaces/ws-001/proj-001/session-001"));
            Files.createDirectories(tempDir.resolve("workspaces/ws-001/proj-001/session-002"));

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            // SessionFinisher returns false - sessions are still active
            when(sessionFinisher.tryFinishFromHeartbeat(any(), eq(PROJECT_INFO), any(), any(), eq(HEARTBEAT_THRESHOLD), eq(NOW)))
                    .thenReturn(false);

            var job = createJob(jeffreyDirs, platformRepositories, sessionFinisher);
            job.executeOnRepository(projectManager, repositoryStorage, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Instance should remain ACTIVE (derived from unfinished sessions)
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find(INSTANCE_ID);
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceStatus.ACTIVE, instance.get().status());
        }
    }

    @Nested
    class HsErrLogDetection {

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        SessionFinisher sessionFinisher;

        @Test
        void hsErrLogPresent_completesWithoutError(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/detector/insert-workspace-project-instance-and-single-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);
            var jeffreyDirs = new JeffreyDirs(tempDir);

            // Create session directory with hs_err log
            Path sessionDir = tempDir.resolve("workspaces/ws-001/proj-001/session-001");
            Files.createDirectories(sessionDir);
            Files.createFile(sessionDir.resolve("hs-jvm-err.log"));

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            // SessionFinisher mock also marks session finished in DB
            var repoRepository = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);
            doAnswer(invocation -> {
                ProjectInstanceSessionInfo session = invocation.getArgument(2);
                repoRepository.markSessionFinished(session.sessionId(), NOW);
                return true;
            }).when(sessionFinisher).tryFinishFromHeartbeat(
                    any(), eq(PROJECT_INFO), any(), any(), eq(HEARTBEAT_THRESHOLD), eq(NOW));

            var job = createJob(jeffreyDirs, platformRepositories, sessionFinisher);

            // Verify the method completes without errors when hs_err log is present
            assertDoesNotThrow(() ->
                    job.executeOnRepository(projectManager, repositoryStorage, JOB_DESCRIPTOR, JobContext.EMPTY));

            // Instance status should derive to FINISHED
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find(INSTANCE_ID);
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceStatus.FINISHED, instance.get().status());
        }
    }

    @Nested
    class NoUnfinishedSessions {

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        SessionFinisher sessionFinisher;

        @Test
        void noUnfinishedSessions_earlyReturn(DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);
            var jeffreyDirs = new JeffreyDirs(tempDir);

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            var job = createJob(jeffreyDirs, platformRepositories, sessionFinisher);
            job.executeOnRepository(projectManager, repositoryStorage, JOB_DESCRIPTOR, JobContext.EMPTY);

            // SessionFinisher should never be called
            verifyNoInteractions(sessionFinisher);
        }
    }

    @Nested
    class PartialSessionFinish {

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryStorage repositoryStorage;

        @Mock
        SessionFinisher sessionFinisher;

        @Test
        void oneSessionFinished_otherStillActive_instanceRemainsActive(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/detector/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);
            var jeffreyDirs = new JeffreyDirs(tempDir);

            Files.createDirectories(tempDir.resolve("workspaces/ws-001/proj-001/session-001"));
            Files.createDirectories(tempDir.resolve("workspaces/ws-001/proj-001/session-002"));

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            var repoRepository = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);

            // First session finishes (also marks in DB), second does not
            doAnswer(invocation -> {
                ProjectInstanceSessionInfo session = invocation.getArgument(2);
                repoRepository.markSessionFinished(session.sessionId(), NOW);
                return true;
            }).when(sessionFinisher).tryFinishFromHeartbeat(
                    any(), eq(PROJECT_INFO),
                    argThat(s -> s.sessionId().equals("session-001")),
                    any(), eq(HEARTBEAT_THRESHOLD), eq(NOW));
            doReturn(false).when(sessionFinisher).tryFinishFromHeartbeat(
                    any(), eq(PROJECT_INFO),
                    argThat(s -> s.sessionId().equals("session-002")),
                    any(), eq(HEARTBEAT_THRESHOLD), eq(NOW));

            var job = createJob(jeffreyDirs, platformRepositories, sessionFinisher);
            job.executeOnRepository(projectManager, repositoryStorage, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Instance should remain ACTIVE (session-002 still unfinished)
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find(INSTANCE_ID);
            assertTrue(instance.isPresent());
            assertEquals(ProjectInstanceStatus.ACTIVE, instance.get().status());
        }
    }
}
