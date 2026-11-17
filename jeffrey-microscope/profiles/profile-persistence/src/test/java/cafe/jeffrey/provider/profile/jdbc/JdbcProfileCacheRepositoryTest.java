/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import tools.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

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
