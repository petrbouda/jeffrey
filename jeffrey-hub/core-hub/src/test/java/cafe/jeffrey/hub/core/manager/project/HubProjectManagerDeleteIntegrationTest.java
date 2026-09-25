/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.manager.project;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cafe.jeffrey.hub.core.manager.HubRepositoryManager;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.jdbc.JdbcHubPlatformRepositories;
import cafe.jeffrey.hub.model.ProjectInfo;
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
 * Project deletion is executed directly: the SQL cascade commits first; the project's
 * directory is removed afterwards (best-effort) — removing the on-disk declaration is what
 * stops the workspace reconciler from re-creating the project.
 */
@DuckDBTest(migration = "classpath:db/migration/hub")
@ExtendWith(MockitoExtension.class)
class HubProjectManagerDeleteIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", null,
            WORKSPACE_ID, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    @Mock
    RepositoryStorage repositoryStorage;

    private record Fixture(
            HubProjectManager manager,
            JdbcHubPlatformRepositories platformRepositories) {
    }

    private Fixture fixture(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        var platformRepositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);

        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        RepositoryManager.Factory repositoryManagerFactory = projectInfo -> new HubRepositoryManager(
                FIXED_CLOCK,
                projectInfo,
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()),
                platformRepositories.newProjectInstanceRepository(projectInfo.id()),
                repositoryStorage,
                transactions);
        var manager = new HubProjectManager(
                PROJECT_INFO, platformRepositories, repositoryStorage, repositoryManagerFactory, transactions);

        return new Fixture(manager, platformRepositories);
    }

    @Nested
    class HappyPath {

        @Test
        void deletesProjectRow_andRemovesDirectory(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            Fixture fixture = fixture(dataSource);

            var projectRepo = fixture.platformRepositories().newProjectRepository(PROJECT_ID);
            assertTrue(projectRepo.find().isPresent());

            fixture.manager().delete();

            assertTrue(projectRepo.find().isEmpty());
            verify(repositoryStorage).deleteProjectDirectory();
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

            assertDoesNotThrow(() -> fixture.manager().delete());

            // Directory removal is best-effort AFTER the commit: leftover files are cheaper
            // than a permanently stuck project delete
            var projectRepo = fixture.platformRepositories().newProjectRepository(PROJECT_ID);
            assertTrue(projectRepo.find().isEmpty());
        }
    }
}
