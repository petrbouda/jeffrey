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

package pbouda.jeffrey.provider.profile.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileCacheRepositoryTest {

    @Nested
    class PutMethod {

        @Test
        void storesSimpleObject(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            repository.put("test-key", Map.of("name", "test", "value", 123));

            assertTrue(repository.contains("test-key"));
        }

        @Test
        void storesList(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            repository.put("list-key", List.of("item1", "item2", "item3"));

            assertTrue(repository.contains("list-key"));
        }

        @Test
        void updatesExistingKey(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            repository.put("update-key", "initial-value");
            repository.put("update-key", "updated-value");

            Optional<String> result = repository.get("update-key", String.class);
            assertTrue(result.isPresent());
            assertEquals("updated-value", result.get());
        }
    }

    @Nested
    class ContainsMethod {

        @Test
        void returnsTrueWhenKeyExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            repository.put("existing-key", "some-value");

            assertTrue(repository.contains("existing-key"));
        }

        @Test
        void returnsFalseWhenKeyNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            assertFalse(repository.contains("non-existing-key"));
        }
    }

    @Nested
    class GetWithClassMethod {

        @Test
        void retrievesStringValue(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            repository.put("string-key", "test-value");

            Optional<String> result = repository.get("string-key", String.class);
            assertTrue(result.isPresent());
            assertEquals("test-value", result.get());
        }

        @Test
        void retrievesIntegerValue(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            repository.put("int-key", 42);

            Optional<Integer> result = repository.get("int-key", Integer.class);
            assertTrue(result.isPresent());
            assertEquals(42, result.get());
        }

        @Test
        void returnsEmptyWhenKeyNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            Optional<String> result = repository.get("missing-key", String.class);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class GetWithTypeReferenceMethod {

        @Test
        void retrievesListOfStrings(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);
            List<String> expected = List.of("a", "b", "c");

            repository.put("list-key", expected);

            Optional<List<String>> result = repository.get("list-key", new TypeReference<>() {});
            assertTrue(result.isPresent());
            assertEquals(expected, result.get());
        }

        @Test
        void retrievesMapOfStringToInteger(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);
            Map<String, Integer> expected = Map.of("one", 1, "two", 2);

            repository.put("map-key", expected);

            Optional<Map<String, Integer>> result = repository.get("map-key", new TypeReference<>() {});
            assertTrue(result.isPresent());
            assertEquals(expected, result.get());
        }

        @Test
        void returnsEmptyWhenKeyNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileCacheRepository repository = new JdbcProfileCacheRepository(provider);

            Optional<List<String>> result = repository.get("missing-key", new TypeReference<>() {});

            assertTrue(result.isEmpty());
        }
    }
}
