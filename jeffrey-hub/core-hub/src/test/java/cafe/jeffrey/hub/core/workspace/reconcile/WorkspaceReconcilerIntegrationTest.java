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
import cafe.jeffrey.hub.core.project.repository.InstanceLifecycleEventEmitter;
import cafe.jeffrey.hub.core.project.repository.SessionFinishEventEmitter;
import cafe.jeffrey.hub.core.streaming.FileHeartbeatReader;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.core.workspace.AuditWorkspaceEventPublisher;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.WorkspaceEventQuery;
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
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    private static final WorkspaceInfo WORKSPACE_INFO = new WorkspaceInfo(
            WORKSPACE_ID, WORKSPACE_ID, null, "Test Workspace", null, null,
            Instant.parse("2025-01-01T10:00:00Z"), null, 0);

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
            JdbcHubPlatformRepositories platformRepositories,
            WorkspaceEventLogRepository eventLog) {
    }

    private Fixture fixture(DataSource dataSource, Path tempDir) {
        var provider = new DatabaseClientProvider(dataSource);
        var platformRepositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);
        WorkspaceEventLogRepository eventLog = platformRepositories.newWorkspaceEventLogRepository();
        var publisher = new AuditWorkspaceEventPublisher(eventLog);

        when(fileHeartbeatReader.readFinishedMarker(any())).thenReturn(Optional.empty());
        when(fileHeartbeatReader.readLastHeartbeat(any())).thenReturn(Optional.empty());

        var sessionFinisher = new SessionFinisher(
                FIXED_CLOCK,
                new SessionFinishEventEmitter(FIXED_CLOCK, publisher),
                new InstanceLifecycleEventEmitter(FIXED_CLOCK, publisher),
                fileHeartbeatReader,
                platformRepositories);

        // Manager layer is mocked but delegates persistence to the real repositories, so
        // materializations land in (and are diffed against) the real database
        ProjectRepositoryRepository repoRepo = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);
        when(projectManager.info()).thenReturn(PROJECT_INFO);
        when(projectManager.repositoryManager()).thenReturn(repositoryManager);
        when(projectManager.projectInstanceRepository())
                .thenReturn(platformRepositories.newProjectInstanceRepository(PROJECT_ID));
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
                publisher,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        return new Fixture(reconciler, platformRepositories, eventLog);
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
        Path instanceDir = createDir(projectDir.resolve(INSTANCE_ID));
        RemoteProjectInstance marker = new RemoteProjectInstance(
                INSTANCE_ID, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                Instant.parse("2025-06-15T10:30:00Z").toEpochMilli(), INSTANCE_ID);
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

    private static List<WorkspaceEvent> eventsOfType(Fixture fixture, WorkspaceEventType type) {
        return fixture.eventLog().findLatest(new WorkspaceEventQuery(WORKSPACE_ID, type, Set.of(), 100));
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

            int materialized = fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);

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

            assertEquals(1, eventsOfType(fixture, WorkspaceEventType.PROJECT_CREATED).size());
            assertEquals(1, eventsOfType(fixture, WorkspaceEventType.PROJECT_INSTANCE_CREATED).size());
            assertEquals(1, eventsOfType(fixture, WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED).size());
        }

        @Test
        void rescanIsANoOp_noDuplicateEntitiesOrAuditRows(
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

            fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);
            long afterFirst = fixture.eventLog().count(WORKSPACE_ID);

            int secondRun = fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);

            assertEquals(0, secondRun, "Everything is already known — nothing to materialize");
            assertEquals(afterFirst, fixture.eventLog().count(WORKSPACE_ID),
                    "A re-scan must not append audit rows");
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

            fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);

            var repoRepo = fixture.platformRepositories().newProjectRepositoryRepository(PROJECT_ID);
            assertTrue(repoRepo.findSessionById("session-100").isEmpty(),
                    "A half-written marker must not materialize the session");

            // The provisioner finishes the write — the next scan converges
            declareSession(instanceDir, "session-100", Instant.parse("2025-06-15T11:00:00Z"), 1);
            fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);

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

            fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);

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

            int materialized = fixture.reconciler().reconcile(WORKSPACE_INFO, projectsManager, wsDir);

            assertEquals(0, materialized);
            assertEquals(0, fixture.eventLog().count(WORKSPACE_ID), "No audit rows for a no-op scan");

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
