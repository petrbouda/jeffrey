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

package cafe.jeffrey.provider.profile.jdbc;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.api.AdvisorRunResultRow;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileAdvisorRepositoryTest {

    private static final Instant WHEN = Instant.parse("2026-08-01T06:00:14Z");

    @Test
    void storesAndReadsBackTheRunResult(DataSource dataSource) {
        JdbcProfileAdvisorRepository repository = repository(dataSource);

        assertFalse(repository.runResultExists());
        assertTrue(repository.findRunResult().isEmpty());

        repository.storeRunResult(new AdvisorRunResultRow("{\"totalElapsedMs\":9100}", WHEN));

        assertTrue(repository.runResultExists());
        Optional<AdvisorRunResultRow> stored = repository.findRunResult();
        assertTrue(stored.isPresent());
        assertEquals("{\"totalElapsedMs\":9100}", stored.get().resultJson());
        assertEquals(WHEN, stored.get().completedAt());
    }

    @Test
    void aNewRunReplacesThePrevious(DataSource dataSource) {
        JdbcProfileAdvisorRepository repository = repository(dataSource);

        repository.storeRunResult(new AdvisorRunResultRow("{\"run\":1}", WHEN));
        repository.storeRunResult(new AdvisorRunResultRow("{\"run\":2}", WHEN));

        assertEquals("{\"run\":2}", repository.findRunResult().orElseThrow().resultJson());
    }

    private static JdbcProfileAdvisorRepository repository(DataSource dataSource) {
        return new JdbcProfileAdvisorRepository(new DatabaseClientProvider(dataSource));
    }
}
