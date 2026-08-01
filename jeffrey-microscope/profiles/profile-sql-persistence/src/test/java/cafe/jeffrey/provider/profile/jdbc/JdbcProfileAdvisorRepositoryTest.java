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

import cafe.jeffrey.provider.profile.api.AdvisorClaimRow;
import cafe.jeffrey.provider.profile.api.AdvisorPromptRow;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileAdvisorRepositoryTest {

    private static final String CPU = "jdk.ExecutionSample";
    private static final String ALLOC = "jdk.ObjectAllocationSample";
    private static final Instant WHEN = Instant.parse("2026-08-01T06:00:14Z");

    private static JdbcProfileAdvisorRepository repository(DataSource dataSource) {
        return new JdbcProfileAdvisorRepository(new DatabaseClientProvider(dataSource));
    }

    private static void seed(JdbcProfileAdvisorRepository repository) {
        repository.upsertPrompt(
                new AdvisorPromptRow(CPU, "CPU", 1_000L, "# markdown", "[]", WHEN));
        repository.upsertRecommendation(
                new AdvisorRecommendationRow(CPU, "HIGH", "report", "abc123", 100L, 200L, 0.42, WHEN));
        repository.replaceClaims(CPU, List.of(
                new AdvisorClaimRow(CPU, "Hot lookup", "RateTable.lookup", "RateTable.java",
                        true, true, 21.5, 30.0, WHEN)));
        repository.replaceClaims(ALLOC, List.of(
                new AdvisorClaimRow(ALLOC, "Churn", "Parser.parse", null, true, false, 12.0, 18.0, WHEN)));
    }

    @Nested
    class DeleteAll {

        @Test
        void dropsPromptsRecommendationsAndClaimsTogether(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);

            repository.deleteAll();

            assertTrue(repository.findPrompts().isEmpty(), "cached prompts must go too");
            assertTrue(repository.findRecommendations().isEmpty());
            assertTrue(repository.findClaims().isEmpty());
        }

        @Test
        void clearsEveryEventTypeRatherThanJustTheFirst(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);
            assertEquals(2, repository.findClaims().size(), "two event types were seeded");

            repository.deleteAll();

            assertTrue(repository.findClaims().isEmpty());
        }

        @Test
        void isANoOpOnAProfileThatNeverRan(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);

            repository.deleteAll();

            assertTrue(repository.findRecommendations().isEmpty());
        }

        @Test
        void leavesTheProfileUsableForAFreshRun(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);
            repository.deleteAll();

            // Clearing must not leave anything behind that a re-run would collide with.
            repository.upsertPrompt(
                    new AdvisorPromptRow(CPU, "CPU", 2_000L, "# rebuilt", "[]", WHEN));

            assertEquals(1, repository.findPrompts().size());
            assertEquals(2_000L, repository.findPrompts().getFirst().samples());
        }
    }
}
