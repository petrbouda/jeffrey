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


package cafe.jeffrey.hub.persistence.jdbc;

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/hub")
class JdbcScopedConfigRepositoryTest {

    private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2025-01-02T12:00:00Z");

    private static final String FIXTURE = "sql/scoped-config/insert-scoped-configs.sql";

    private static JdbcScopedConfigRepository repository(DataSource dataSource) {
        return new JdbcScopedConfigRepository(new DatabaseClientProvider(dataSource));
    }

    private static ScopedConfigEntry entry(ScopedConfigKey key, String value, Instant at) {
        return new ScopedConfigEntry(key, ConfigType.ASPROF_SETTINGS, value, at);
    }

    @Nested
    class Upsert {

        @Test
        void storesAValueAtEachScope(DataSource dataSource) {
            JdbcScopedConfigRepository repository = repository(dataSource);

            repository.upsert(entry(ScopedConfigKey.global(), "global", NOW));
            repository.upsert(entry(ScopedConfigKey.workspace("ws-001"), "workspace", NOW));
            repository.upsert(entry(ScopedConfigKey.project("ws-001", "proj-001"), "project", NOW));

            assertEquals("global", only(repository.find(ScopedConfigKey.global())));
            assertEquals("workspace", only(repository.find(ScopedConfigKey.workspace("ws-001"))));
            assertEquals("project", only(repository.find(ScopedConfigKey.project("ws-001", "proj-001"))));
        }

        /**
         * The ids are NULL where a scope has none, and a UNIQUE over nullable columns counts each
         * NULL as distinct — so without the computed key an upsert at the global scope would append
         * a row every time instead of replacing one.
         */
        @Test
        void replacesTheValueRatherThanAppendingARow(DataSource dataSource) throws SQLException {
            JdbcScopedConfigRepository repository = repository(dataSource);

            repository.upsert(entry(ScopedConfigKey.global(), "first", NOW));
            repository.upsert(entry(ScopedConfigKey.global(), "second", LATER));

            List<ScopedConfigEntry> entries = repository.find(ScopedConfigKey.global());
            assertEquals(1, entries.size());
            assertEquals("second", entries.getFirst().value());
            assertEquals(LATER, entries.getFirst().updatedAt());
        }

        @Test
        void keepsTheScopeOfEachEntry(DataSource dataSource) {
            JdbcScopedConfigRepository repository = repository(dataSource);
            ScopedConfigKey key = ScopedConfigKey.project("ws-001", "proj-001");

            repository.upsert(entry(key, "project", NOW));

            assertEquals(key, repository.find(key).getFirst().key());
        }
    }

    @Nested
    class Find {

        @Test
        void aScopeWithNothingStoredHasNoEntries(DataSource dataSource) {
            assertTrue(repository(dataSource).find(ScopedConfigKey.global()).isEmpty());
        }

        @Test
        void readsEachScopeFromTheFixture(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            JdbcScopedConfigRepository repository = repository(dataSource);

            assertEquals("global-settings", only(repository.find(ScopedConfigKey.global())));
            assertEquals("workspace-settings", only(repository.find(ScopedConfigKey.workspace("ws-001"))));
            assertEquals("project-settings", only(repository.find(ScopedConfigKey.project("ws-001", "proj-001"))));
        }

        /**
         * A global scope must not be answered with a workspace's row, which is what equality on a
         * NULL id would do if the statement used {@code =} instead of IS NOT DISTINCT FROM.
         */
        @Test
        void doesNotConfuseAGlobalScopeWithAWorkspaceOne(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);

            assertEquals("global-settings", only(repository(dataSource).find(ScopedConfigKey.global())));
        }
    }

    @Nested
    class FindForWorkspace {

        @Test
        void returnsTheGlobalTheWorkspaceAndItsProjects(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);

            List<ScopedConfigEntry> entries = repository(dataSource).findForWorkspace("ws-001");

            assertEquals(3, entries.size());
        }

        @Test
        void leavesOutAnotherWorkspacesEntries(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            JdbcScopedConfigRepository repository = repository(dataSource);
            repository.upsert(entry(ScopedConfigKey.workspace("ws-002"), "other", NOW));

            List<ScopedConfigEntry> entries = repository.findForWorkspace("ws-001");

            assertTrue(entries.stream().noneMatch(e -> "ws-002".equals(e.key().workspaceId())));
        }
    }

    @Nested
    class Delete {

        @Test
        void removesOneValue(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            JdbcScopedConfigRepository repository = repository(dataSource);

            repository.delete(ScopedConfigKey.workspace("ws-001"), ConfigType.ASPROF_SETTINGS);

            assertTrue(repository.find(ScopedConfigKey.workspace("ws-001")).isEmpty());
            assertEquals("global-settings", only(repository.find(ScopedConfigKey.global())));
        }

        @Test
        void removingWhatIsNotThereSucceeds(DataSource dataSource) {
            repository(dataSource).delete(ScopedConfigKey.global(), ConfigType.ASPROF_SETTINGS);
        }

        @Test
        void deleteAllRemovesEverythingOneScopeHolds(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, FIXTURE);
            JdbcScopedConfigRepository repository = repository(dataSource);

            repository.deleteAll(ScopedConfigKey.project("ws-001", "proj-001"));

            assertTrue(repository.find(ScopedConfigKey.project("ws-001", "proj-001")).isEmpty());
            assertEquals("workspace-settings", only(repository.find(ScopedConfigKey.workspace("ws-001"))));
        }
    }

    private static String only(List<ScopedConfigEntry> entries) {
        assertEquals(1, entries.size(), "expected exactly one entry");
        return entries.getFirst().value();
    }
}
