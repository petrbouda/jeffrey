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

package cafe.jeffrey.hub.core.workspace.reconcile;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.streaming.FileHeartbeatReader;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.persistence.jdbc.JdbcHubPlatformRepositories;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.CreateProject;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The reconciler materializes on-disk workspace declarations (marker files) into the
 * database — create-only, idempotent by natural keys, one transaction per entity.
 */
@DuckDBTest(migration = "classpath:db/migration/server")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceReconcilerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String INSTANCE_ID = "inst-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    private static final RepositoryInfo REPO_INFO = new RepositoryInfo(
            "repo-001", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    @Mock
    FileHeartbeatReader fileHeartbeatReader;

    private record Fixture(
            WorkspaceReconciler reconciler,
            JdbcHubPlatformRepositories platformRepositories) {
    }

    private Fixture fixture(DataSource dataSource, Path tempDir) {
        var provider = new DatabaseClientProvider(dataSource);
        return fixture(dataSource, tempDir, new JdbcHubPlatformRepositories(provider, FIXED_CLOCK));
    }

    private Fixture fixture(
            DataSource dataSource, Path tempDir, JdbcHubPlatformRepositories platformRepositories) {

        when(fileHeartbeatReader.readFinishedMarker(any())).thenReturn(Optional.empty());
        when(fileHeartbeatReader.readLastHeartbeat(any())).thenReturn(Optional.empty());

        var sessionFinisher = new SessionFinisher(
                FIXED_CLOCK,
                fileHeartbeatReader,
                platformRepositories);

        // Manager layer is mocked but delegates persistence to the real repositories, so
        // materializations land in (and are diffed against) the real database
        // Resolved before stubbing: on a spied platformRepositories these calls inside a
        // when(...) expression would read as unfinished stubbing
        ProjectRepositoryRepository repoRepo = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);
        var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);

        when(projectManager.info()).thenReturn(PROJECT_INFO);
        when(projectManager.repositoryManager()).thenReturn(repositoryManager);
        when(projectManager.projectInstanceRepository()).thenReturn(instanceRepo);
        when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));
        doAnswer(invocation -> {
            repoRepo.createSession(invocation.getArgument(0));
            return null;
        }).when(repositoryManager).createSession(any(ProjectInstanceSessionInfo.class));

        var reconciler = new WorkspaceReconciler(
                FIXED_CLOCK,
                new HubJeffreyDirs(tempDir),
                platformRepositories,
                sessionFinisher,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        return new Fixture(reconciler, platformRepositories);
    }

    // ---------- On-disk declaration helpers ----------

    private static Path workspaceDir(Path tempDir) {
        return createDir(tempDir.resolve("workspaces").resolve(WORKSPACE_ID));
    }

    private static Path declareProject(Path workspaceDir) {
        Path projectDir = createDir(workspaceDir.resolve("proj-001"));
        RemoteProject marker = new RemoteProject(
                ORIGIN_PROJECT_ID, "project-alpha", "Alpha Label", WORKSPACE_ID,
                Instant.parse("2025-06-15T10:00:00Z").toEpochMilli(),
                "/workspaces", "ws-001", "proj-001",
                RepositoryType.ASYNC_PROFILER, Map.of("env", "prod"));
        write(projectDir.resolve(".project-info.json"), Json.toString(marker));
        return projectDir;
    }

    private static Path declareInstance(Path projectDir) {
        return declareInstance(projectDir, INSTANCE_ID);
    }

    private static Path declareInstance(Path projectDir, String instanceId) {
        Path instanceDir = createDir(projectDir.resolve(instanceId));
        RemoteProjectInstance marker = new RemoteProjectInstance(
                instanceId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                Instant.parse("2025-06-15T10:30:00Z").toEpochMilli(), instanceId);
        write(instanceDir.resolve(".instance-info.json"), Json.toString(marker));
        return instanceDir;
    }

    private static void declareSession(Path instanceDir, String sessionId, Instant createdAt, int order) {
        Path sessionDir = createDir(instanceDir.resolve(sessionId));
        RemoteProjectInstanceSession marker = new RemoteProjectInstanceSession(
                sessionId, ORIGIN_PROJECT_ID, WORKSPACE_ID, INSTANCE_ID,
                createdAt.toEpochMilli(), order, INSTANCE_ID + "/" + sessionId, "GLOBAL", "cmd");
        write(sessionDir.resolve(".session-info.json"), Json.toString(marker));
    }

    private static Path createDir(Path dir) {
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The mocked {@code repositoryManager.info()} reports a repository, bypassing the
     * reconciler's own repository-repair path — insert the row it claims to exist, since
     * {@code findSessionById} joins the repositories table.
     */
    private static void insertRepositoryRow(DataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO repositories (project_id, repository_id, repository_type,
                        workspaces_path, relative_workspace_path, relative_project_path)
                    VALUES ('proj-001', 'repo-001', 'ASYNC_PROFILER', '/workspaces', 'ws-001', 'proj-001')""");
        }
    }

    @Nested
    class Materialization {

        @Test
        void fullTreeScan_materializesProjectInstanceAndSession_withAuditRows(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            insertRepositoryRow(dataSource);
            Fixture fixture = fixture(dataSource, tempDir);

            Path wsDir = workspaceDir(tempDir);
            Path projectDir = declareProject(wsDir);
            Path instanceDir = declareInstance(projectDir);
            declareSession(instanceDir, "session-100", Instant.parse("2025-06-15T11:00:00Z"), 1);

            // Project is unknown to the manager layer at first sight — it gets created
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.empty());
            when(projectsManager.create(any())).thenReturn(projectManager);

            int materialized = fixture.reconciler().reconcile(projectsManager, wsDir);

            assertEquals(3, materialized);

            ArgumentCaptor<CreateProject> captor = ArgumentCaptor.forClass(CreateProject.class);
            verify(projectsManager).create(captor.capture());
            assertAll(
                    () -> assertEquals(ORIGIN_PROJECT_ID, captor.getValue().originProjectId()),
                    () -> assertEquals("project-alpha", captor.getValue().projectName()),
                    () -> assertEquals(Map.of("env", "prod"), captor.getValue().attributes()));

            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.ACTIVE, instanceRepo.find(INSTANCE_ID).orElseThrow().status(),
                    "Session materialization must flip the instance to ACTIVE");

            var repoRepo = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            assertTrue(repoRepo.findSessionById("session-100").isPresent());
        }

        @Test
        void rescanIsANoOp_noDuplicateEntities(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            Fixture fixture = fixture(dataSource, tempDir);

            Path wsDir = workspaceDir(tempDir);
            Path projectDir = declareProject(wsDir);
            Path instanceDir = declareInstance(projectDir);
            declareSession(instanceDir, "session-100", Instant.parse("2025-06-15T11:00:00Z"), 1);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(projectManager));
            when(projectsManager.create(any())).thenReturn(projectManager);

            fixture.reconciler().reconcile(projectsManager, wsDir);

            int secondRun = fixture.reconciler().reconcile(projectsManager, wsDir);

            assertEquals(0, secondRun, "Everything is already known — nothing to materialize");
            verify(projectsManager, times(1)).create(any());
            verify(repositoryManager, times(1)).createSession(any(ProjectInstanceSessionInfo.class));
        }

        @Test
        void halfWrittenMarker_skipsSubtree_andConvergesAfterCompletion(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            insertRepositoryRow(dataSource);
            Fixture fixture = fixture(dataSource, tempDir);

            Path wsDir = workspaceDir(tempDir);
            Path projectDir = declareProject(wsDir);
            Path instanceDir = declareInstance(projectDir);
            Path sessionDir = createDir(instanceDir.resolve("session-100"));
            write(sessionDir.resolve(".session-info.json"), "{ this is not json");

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.of(projectManager));

            fixture.reconciler().reconcile(projectsManager, wsDir);

            var repoRepo = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            assertTrue(repoRepo.findSessionById("session-100").isEmpty(),
                    "A half-written marker must not materialize the session");

            // The provisioner finishes the write — the next scan converges
            declareSession(instanceDir, "session-100", Instant.parse("2025-06-15T11:00:00Z"), 1);
            fixture.reconciler().reconcile(projectsManager, wsDir);

            assertTrue(repoRepo.findSessionById("session-100").isPresent());
        }

        @Test
        void newSession_forceFinishesPriorUnfinishedSessions_withChainedFallbacks(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/reconciliation/insert-project-with-multiple-unfinished-sessions.sql");
            Fixture fixture = fixture(dataSource, tempDir);

            Path wsDir = workspaceDir(tempDir);
            Path projectDir = declareProject(wsDir);
            Path instanceDir = declareInstance(projectDir);
            // Declare the three known sessions too — they are already in the database
            declareSession(instanceDir, "session-001", Instant.parse("2025-06-15T08:00:00Z"), 1);
            declareSession(instanceDir, "session-002", Instant.parse("2025-06-15T09:00:00Z"), 2);
            declareSession(instanceDir, "session-003", Instant.parse("2025-06-15T10:00:00Z"), 3);
            Instant newSessionCreatedAt = Instant.parse("2025-06-15T11:00:00Z");
            declareSession(instanceDir, "session-004", newSessionCreatedAt, 4);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.of(projectManager));

            fixture.reconciler().reconcile(projectsManager, wsDir);

            var repoRepo = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            // Each unfinished session's fallback finished_at is the NEXT session's originCreatedAt;
            // the last one gets the new session's originCreatedAt
            assertEquals(Instant.parse("2025-06-15T09:00:00Z"),
                    repoRepo.findSessionById("session-001").orElseThrow().finishedAt());
            assertEquals(Instant.parse("2025-06-15T10:00:00Z"),
                    repoRepo.findSessionById("session-002").orElseThrow().finishedAt());
            assertEquals(newSessionCreatedAt,
                    repoRepo.findSessionById("session-003").orElseThrow().finishedAt());
            assertNull(repoRepo.findSessionById("session-004").orElseThrow().finishedAt(),
                    "The new session itself stays unfinished");
        }
    }

    /**
     * The scan runs on a short period, so its cost must not grow with the size of the tree.
     * What the database already knows is read once per project; instances and sessions the
     * database has confirmed are recognized without a query and without opening their markers.
     */
    @Nested
    class ScanCost {

        @Test
        void knownEntities_areReadPerProject_notPerInstance(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            insertRepositoryRow(dataSource);

            var provider = new DatabaseClientProvider(dataSource);
            var spiedRepositories = spy(new JdbcHubPlatformRepositories(provider, FIXED_CLOCK));
            Fixture fixture = fixture(dataSource, tempDir, spiedRepositories);

            Path wsDir = workspaceDir(tempDir);
            Path projectDir = declareProject(wsDir);
            declareInstance(projectDir, "inst-001");
            declareInstance(projectDir, "inst-002");
            declareInstance(projectDir, "inst-003");

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.of(projectManager));

            fixture.reconciler().reconcile(projectsManager, wsDir);

            verify(spiedRepositories, never()).findSessionsByInstanceId(anyString());
        }

        @Test
        void knownInstance_isRecognizedWithoutItsMarker(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            insertRepositoryRow(dataSource);
            Fixture fixture = fixture(dataSource, tempDir);

            Path wsDir = workspaceDir(tempDir);
            Path projectDir = declareProject(wsDir);
            Path instanceDir = declareInstance(projectDir);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.of(projectManager));

            fixture.reconciler().reconcile(projectsManager, wsDir);

            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertTrue(instanceRepo.find(INSTANCE_ID).isPresent());

            // The marker is gone, but the row is the authority: the instance stays known and the
            // scan neither fails nor re-materializes it
            assertTrue(Files.deleteIfExists(instanceDir.resolve(".instance-info.json")));

            int secondRun = fixture.reconciler().reconcile(projectsManager, wsDir);

            assertEquals(0, secondRun);
            assertTrue(instanceRepo.find(INSTANCE_ID).isPresent());
        }
    }

    @Nested
    class CreateOnlyGuarantee {

        @Test
        void removedDirectories_neverDeleteHubState(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException {
            // Database knows a project, an instance and sessions; the disk declares NOTHING
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource, tempDir);

            Path wsDir = workspaceDir(tempDir);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID))
                    .thenReturn(Optional.of(projectManager));

            int materialized = fixture.reconciler().reconcile(projectsManager, wsDir);

            assertEquals(0, materialized);

            // Hub state is untouched: rows, statuses, sessions all survive the empty disk
            var instanceRepo = fixture.platformRepositories().newProjectInstanceRepository(PROJECT_ID);
            assertEquals(ProjectInstanceStatus.ACTIVE, instanceRepo.find(INSTANCE_ID).orElseThrow().status());
            var repoRepo = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            assertTrue(repoRepo.findSessionById("session-001").isPresent());
            assertTrue(repoRepo.findSessionById("session-002").isPresent());
            verify(projectsManager, never()).create(any());
            verify(repositoryManager, never()).createSession(any(ProjectInstanceSessionInfo.class));
        }
    }
}
