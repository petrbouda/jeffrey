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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcAdvisorProjectFolderRepositoryTest {

    private static final String FOLDER_ID = "019fb9d3-1da5-7794-b950-ccc7d236db11";
    private static final String OTHER_FOLDER_ID = "019fb9d3-1da5-7794-b950-ccc7d236db22";
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T09:00:00Z");

    @Nested
    class Insert {

        @Test
        void storesAndReadsBackAFolder(DataSource dataSource) {
            JdbcAdvisorProjectFolderRepository repository = createRepository(dataSource);

            repository.insert(folder(FOLDER_ID, "order-service", "/home/me/order-service"));

            Optional<AdvisorProjectFolder> result = repository.find(FOLDER_ID);
            assertTrue(result.isPresent());
            assertEquals("order-service", result.get().name());
            assertEquals("/home/me/order-service", result.get().path());
            assertEquals(CREATED_AT, result.get().createdAt());
        }

        // The name identifies a folder in the Advisor picker, so the database refuses a duplicate even
        // if the manager's check were ever bypassed.
        @Test
        void rejectsADuplicateName(DataSource dataSource) {
            JdbcAdvisorProjectFolderRepository repository = createRepository(dataSource);

            repository.insert(folder(FOLDER_ID, "order-service", "/home/me/order-service"));

            assertThrows(RuntimeException.class,
                    () -> repository.insert(folder(OTHER_FOLDER_ID, "order-service", "/home/me/elsewhere")));
        }
    }

    @Nested
    class FindAll {

        @Test
        void ordersByName(DataSource dataSource) {
            JdbcAdvisorProjectFolderRepository repository = createRepository(dataSource);

            repository.insert(folder(FOLDER_ID, "payment-gateway", "/home/me/payment-gateway"));
            repository.insert(folder(OTHER_FOLDER_ID, "order-service", "/home/me/order-service"));

            List<AdvisorProjectFolder> result = repository.findAll();
            assertEquals(List.of("order-service", "payment-gateway"), result.stream().map(AdvisorProjectFolder::name).toList());
        }

        @Test
        void returnsEmptyWhenNothingWasAdded(DataSource dataSource) {
            assertTrue(createRepository(dataSource).findAll().isEmpty());
        }
    }

    @Nested
    class FindByName {

        @Test
        void findsTheFolderHoldingTheName(DataSource dataSource) {
            JdbcAdvisorProjectFolderRepository repository = createRepository(dataSource);

            repository.insert(folder(FOLDER_ID, "order-service", "/home/me/order-service"));

            Optional<AdvisorProjectFolder> result = repository.findByName("order-service");
            assertTrue(result.isPresent());
            assertEquals(FOLDER_ID, result.get().folderId());
        }

        @Test
        void returnsEmptyForAnUnusedName(DataSource dataSource) {
            assertTrue(createRepository(dataSource).findByName("order-service").isEmpty());
        }
    }

    @Nested
    class Update {

        @Test
        void rewritesNameAndPath(DataSource dataSource) {
            JdbcAdvisorProjectFolderRepository repository = createRepository(dataSource);

            repository.insert(folder(FOLDER_ID, "order-service", "/home/me/order-service"));
            repository.update(folder(FOLDER_ID, "Order Service", "/home/me/work/order-service"));

            Optional<AdvisorProjectFolder> result = repository.find(FOLDER_ID);
            assertTrue(result.isPresent());
            assertEquals("Order Service", result.get().name());
            assertEquals("/home/me/work/order-service", result.get().path());
        }
    }

    @Nested
    class Delete {

        @Test
        void removesTheFolder(DataSource dataSource) {
            JdbcAdvisorProjectFolderRepository repository = createRepository(dataSource);

            repository.insert(folder(FOLDER_ID, "order-service", "/home/me/order-service"));
            repository.delete(FOLDER_ID);

            assertTrue(repository.find(FOLDER_ID).isEmpty());
        }
    }

    private static AdvisorProjectFolder folder(String folderId, String name, String path) {
        return new AdvisorProjectFolder(folderId, name, path, CREATED_AT);
    }

    private static JdbcAdvisorProjectFolderRepository createRepository(DataSource dataSource) {
        return new JdbcAdvisorProjectFolderRepository(new DatabaseClientProvider(dataSource));
    }
}
