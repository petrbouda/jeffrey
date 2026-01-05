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

package pbouda.jeffrey.provider.platform.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcProfilerRepositoryTest {

    @Nested
    class UpsertSettingsMethod {

        @Test
        void insertsNewSettings(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            ProfilerInfo profilerInfo = new ProfilerInfo("ws-001", "proj-001", "cpu=true");
            repository.upsertSettings(profilerInfo);

            List<ProfilerInfo> result = repository.findAllSettings();
            assertEquals(1, result.size());
            assertEquals("cpu=true", result.get(0).agentSettings());
        }

        @Test
        void updatesExistingSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            ProfilerInfo profilerInfo = new ProfilerInfo("ws-001", "proj-001", "cpu=true,alloc=true");
            repository.upsertSettings(profilerInfo);

            List<ProfilerInfo> result = repository.fetchProfilerSettings("ws-001", "proj-001");
            // Should return all matching settings (global, workspace, project)
            ProfilerInfo projectSettings = result.stream()
                    .filter(p -> "ws-001".equals(p.workspaceId()) && "proj-001".equals(p.projectId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("cpu=true,alloc=true", projectSettings.agentSettings());
        }

        @Test
        void insertsGlobalSettings(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            ProfilerInfo profilerInfo = new ProfilerInfo(null, null, "global-cpu=true");
            repository.upsertSettings(profilerInfo);

            List<ProfilerInfo> result = repository.findAllSettings();
            assertEquals(1, result.size());
            assertNull(result.get(0).workspaceId());
            assertNull(result.get(0).projectId());
        }
    }

    @Nested
    class FetchProfilerSettingsMethod {

        @Test
        void returnsMatchingSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            List<ProfilerInfo> result = repository.fetchProfilerSettings("ws-001", "proj-001");

            // Should return global, workspace, and project settings
            assertEquals(3, result.size());
        }

        @Test
        void returnsGlobalAndWorkspaceSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            List<ProfilerInfo> result = repository.fetchProfilerSettings("ws-001", "proj-other");

            // Should return only global and workspace-level settings
            assertEquals(2, result.size());
        }
    }

    @Nested
    class FindWorkspaceSettingsMethod {

        @Test
        void returnsWorkspaceAndGlobalSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            List<ProfilerInfo> result = repository.findWorkspaceSettings("ws-001");

            // Should return global and all workspace-related settings
            assertTrue(result.size() >= 2);
        }
    }

    @Nested
    class FindAllSettingsMethod {

        @Test
        void returnsEmptyList_whenNoSettings(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            List<ProfilerInfo> result = repository.findAllSettings();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsAllSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            List<ProfilerInfo> result = repository.findAllSettings();

            assertEquals(3, result.size());
        }
    }

    @Nested
    class DeleteSettingsMethod {

        @Test
        void deletesSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            repository.deleteSettings("ws-001", "proj-001");

            List<ProfilerInfo> result = repository.findAllSettings();
            assertEquals(2, result.size()); // global and workspace settings remain
        }

        @Test
        void deletesGlobalSettings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/profiler/insert-profiler-settings.sql");
            JdbcProfilerRepository repository = new JdbcProfilerRepository(provider);

            repository.deleteSettings(null, null);

            List<ProfilerInfo> result = repository.findAllSettings();
            assertEquals(2, result.size()); // workspace and project settings remain
        }
    }
}
