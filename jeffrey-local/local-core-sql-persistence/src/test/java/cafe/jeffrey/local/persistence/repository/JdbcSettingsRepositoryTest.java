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

package cafe.jeffrey.local.persistence.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.local.persistence.model.Setting;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/local/core")
class JdbcSettingsRepositoryTest {

    @Nested
    class Upsert {

        @Test
        void insertsNewSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));

            Optional<Setting> result = repository.find("ai", "jeffrey.local.ai.provider");
            assertTrue(result.isPresent());
            assertEquals("claude", result.get().value());
            assertFalse(result.get().secret());
        }

        @Test
        void updatesExistingSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));
            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "chatgpt", false));

            Optional<Setting> result = repository.find("ai", "jeffrey.local.ai.provider");
            assertTrue(result.isPresent());
            assertEquals("chatgpt", result.get().value());
        }

        @Test
        void insertsSecretSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("ai", "jeffrey.local.ai.api-key", "encrypted-value", true));

            Optional<Setting> result = repository.find("ai", "jeffrey.local.ai.api-key");
            assertTrue(result.isPresent());
            assertTrue(result.get().secret());
            assertEquals("encrypted-value", result.get().value());
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

            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));
            repository.upsert(new Setting("ai", "jeffrey.local.ai.model", "opus", false));
            repository.upsert(new Setting("logging", "logging.level.cafe.jeffrey", "DEBUG", false));

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

            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));
            repository.upsert(new Setting("ai", "jeffrey.local.ai.model", "opus", false));
            repository.upsert(new Setting("logging", "logging.level.cafe.jeffrey", "DEBUG", false));

            List<Setting> aiSettings = repository.findByCategory("ai");
            assertEquals(2, aiSettings.size());
            assertTrue(aiSettings.stream().allMatch(s -> "ai".equals(s.category())));
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesSingleSetting(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));
            repository.upsert(new Setting("ai", "jeffrey.local.ai.model", "opus", false));

            repository.delete("ai", "jeffrey.local.ai.provider");

            assertTrue(repository.find("ai", "jeffrey.local.ai.provider").isEmpty());
            assertTrue(repository.find("ai", "jeffrey.local.ai.model").isPresent());
        }
    }

    @Nested
    class DeleteByCategory {

        @Test
        void deletesAllInCategory(DataSource dataSource) {
            JdbcSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));
            repository.upsert(new Setting("ai", "jeffrey.local.ai.model", "opus", false));
            repository.upsert(new Setting("logging", "logging.level.cafe.jeffrey", "DEBUG", false));

            repository.deleteByCategory("ai");

            assertTrue(repository.findByCategory("ai").isEmpty());
            assertEquals(1, repository.findByCategory("logging").size());
        }
    }

    private static JdbcSettingsRepository createRepository(DataSource dataSource) {
        return new JdbcSettingsRepository(new DatabaseClientProvider(dataSource));
    }
}
