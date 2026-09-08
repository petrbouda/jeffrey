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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.Setting;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcSettingsRepositoryTest {

    @Nested
    class Upsert {

        @Test
        void insertsNewSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "single-line"));

            Optional<Setting> result = repository.find("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode");
            assertTrue(result.isPresent());
            assertEquals("single-line", result.get().value());
        }

        @Test
        void updatesExistingSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "single-line"));
            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "two-line"));

            Optional<Setting> result = repository.find("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode");
            assertTrue(result.isPresent());
            assertEquals("two-line", result.get().value());
        }
    }

    @Nested
    class Find {

        @Test
        void returnsEmptyWhenNotFound(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            Optional<Setting> result = repository.find("nonexistent", "nonexistent.key");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FindAll {

        @Test
        void returnsAllSettings(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "single-line"));
            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct", "0.05"));
            repository.upsert(new Setting("logging", "logging.level.cafe.jeffrey", "DEBUG"));

            List<Setting> all = repository.findAll();
            assertEquals(3, all.size());
        }

        @Test
        void returnsEmptyListWhenNoSettings(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            List<Setting> all = repository.findAll();
            assertTrue(all.isEmpty());
        }
    }

    @Nested
    class FindByCategory {

        @Test
        void returnsOnlyMatchingCategory(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "single-line"));
            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct", "0.05"));
            repository.upsert(new Setting("logging", "logging.level.cafe.jeffrey", "DEBUG"));

            List<Setting> visualization = repository.findByCategory("visualization");
            assertEquals(2, visualization.size());
            assertTrue(visualization.stream().allMatch(s -> "visualization".equals(s.category())));
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesSingleSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "single-line"));
            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct", "0.05"));

            repository.delete("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode");

            assertTrue(repository.find("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode").isEmpty());
            assertTrue(repository.find("visualization", "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct").isPresent());
        }
    }

    @Nested
    class DeleteByCategory {

        @Test
        void deletesAllInCategory(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.frame-text-mode", "single-line"));
            repository.upsert(new Setting("visualization", "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct", "0.05"));
            repository.upsert(new Setting("logging", "logging.level.cafe.jeffrey", "DEBUG"));

            repository.deleteByCategory("visualization");

            assertTrue(repository.findByCategory("visualization").isEmpty());
            assertEquals(1, repository.findByCategory("logging").size());
        }
    }

    private static JdbcSettingsRepository createRepository(DataSource dataSource) {
        return new JdbcSettingsRepository(new DatabaseClientProvider(dataSource));
    }
}
