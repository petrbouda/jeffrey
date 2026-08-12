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

package cafe.jeffrey.hub.core.manager.project;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cafe.jeffrey.hub.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.hub.core.project.repository.InstanceLifecycleEventEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.workspace.AuditWorkspaceEventPublisher;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.WorkspaceEventQuery;
import cafe.jeffrey.hub.persistence.jdbc.JdbcHubPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Project deletion is executed directly: the SQL cascade and the audit event commit in one
 * transaction; the project's directory is removed afterwards (best-effort) — removing the
 * on-disk declaration is what stops the workspace reconciler from re-creating the project.
 */
@DuckDBTest(migration = "classpath:db/migration/server")
@ExtendWith(MockitoExtension.class)
class HubProjectManagerDeleteIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    @Mock
    RepositoryStorage repositoryStorage;

    private record Fixture(
            HubProjectManager manager,
            JdbcHubPlatformRepositories platformRepositories,
            WorkspaceEventLogRepository eventLog) {
    }

    private Fixture fixture(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        var platformRepositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);
        WorkspaceEventLogRepository eventLog = platformRepositories.newWorkspaceEventLogRepository();
        var publisher = new AuditWorkspaceEventPublisher(eventLog);

        var manager = new HubProjectManager(
                FIXED_CLOCK,
                PROJECT_INFO,
                platformRepositories,
                repositoryStorage,
                publisher,
                mock(InstanceEnvironmentParser.class),
                new InstanceLifecycleEventEmitter(FIXED_CLOCK, publisher),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        return new Fixture(manager, platformRepositories, eventLog);
    }

    @Nested
    class HappyPath {

        @Test
        void deletesProjectRow_removesDirectory_andWritesAuditRow(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource);

            var projectRepo = fixture.platformRepositories().newProjectRepository(PROJECT_ID);
            assertTrue(projectRepo.find().isPresent());

            fixture.manager().delete(WorkspaceEventCreator.MANUAL);

            assertTrue(projectRepo.find().isEmpty());
            verify(repositoryStorage).deleteProjectDirectory();

            List<WorkspaceEvent> deleted = fixture.eventLog().findLatest(
                    new WorkspaceEventQuery(WORKSPACE_ID, WorkspaceEventType.PROJECT_DELETED, Set.of(), 10));
            assertEquals(1, deleted.size());
            assertEquals(PROJECT_ID, deleted.getFirst().originEventId());
        }
    }

    @Nested
    class StorageFailure {

        @Test
        void directoryRemovalFailure_doesNotUndoTheDatabaseDelete(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource);

            doThrow(new RuntimeException("Storage unavailable"))
                    .when(repositoryStorage).deleteProjectDirectory();

            assertDoesNotThrow(() -> fixture.manager().delete(WorkspaceEventCreator.MANUAL));

            // Directory removal is best-effort AFTER the commit: leftover files are cheaper
            // than a permanently stuck project delete
            var projectRepo = fixture.platformRepositories().newProjectRepository(PROJECT_ID);
            assertTrue(projectRepo.find().isEmpty());
            assertEquals(1, fixture.eventLog().count(WORKSPACE_ID));
        }
    }
}
