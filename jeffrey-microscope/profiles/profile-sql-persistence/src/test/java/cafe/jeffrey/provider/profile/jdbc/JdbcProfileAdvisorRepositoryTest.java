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

import cafe.jeffrey.provider.profile.api.AdvisorPromptRow;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileAdvisorRepositoryTest {

    private static final String CPU = "jdk.ExecutionSample";
    private static final String ALLOC = "jdk.ObjectAllocationSample";
    private static final Instant WHEN = Instant.parse("2026-08-01T06:00:14Z");
    private static final String HOT_METHOD = "com.acme.rates.RateTable.lookup";
    private static final String PATCH = """
            --- a/RateTable.java
            +++ b/RateTable.java
            @@ -1,1 +1,1 @@
            -slow
            +fast
            """;

    private static JdbcProfileAdvisorRepository repository(DataSource dataSource) {
        return new JdbcProfileAdvisorRepository(new DatabaseClientProvider(dataSource));
    }

    private static void seed(JdbcProfileAdvisorRepository repository) {
        repository.upsertPrompt(
                new AdvisorPromptRow(CPU, "CPU", 1_000L, "the user message", 21.5, HOT_METHOD, WHEN));
        repository.upsertPrompt(
                new AdvisorPromptRow(ALLOC, "Allocation", 400L, "the user message", 12.0, HOT_METHOD, WHEN));
        repository.upsertRecommendation(
                new AdvisorRecommendationRow(CPU, "HIGH", 21.5, HOT_METHOD, "report", PATCH, "abc123", WHEN));
    }

    @Nested
    class Prompts {

        @Test
        void roundTripsThePromptAndTheShareSeverityIsGradedFrom(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);

            AdvisorPromptRow row = repository.findPrompt(CPU).orElseThrow();

            assertEquals("the user message", row.prompt());
            assertEquals(21.5, row.dominantSelfPct());
            assertEquals(HOT_METHOD, row.dominantMethod(),
                    "the share is only readable next to the method holding it");
        }
    }

    @Nested
    class Recommendations {

        @Test
        void roundTripsTheProposedPatch(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);

            assertEquals(PATCH, repository.findRecommendations().getFirst().patch());
        }

        @Test
        void keepsAMissingPatchNullRatherThanEmpty(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            repository.upsertRecommendation(
                    new AdvisorRecommendationRow(ALLOC, "LOW", 1.2, HOT_METHOD, "report", null, "abc123", WHEN));

            assertNull(repository.findRecommendations().getFirst().patch());
        }

        @Test
        void replacesThePatchOnARerun(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);

            repository.upsertRecommendation(
                    new AdvisorRecommendationRow(
                            CPU, "LOW", 21.5, HOT_METHOD, "second report", null, "def456", WHEN));

            assertEquals(1, repository.findRecommendations().size());
            assertNull(repository.findRecommendations().getFirst().patch(),
                    "a re-run that proposes no edit must clear the previous patch");
        }
    }

    @Nested
    class DeleteAll {

        @Test
        void dropsPromptsAndRecommendationsTogether(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);

            repository.deleteAll();

            assertTrue(repository.findPrompts().isEmpty(), "cached prompts must go too");
            assertTrue(repository.findRecommendations().isEmpty());
        }

        @Test
        void clearsEveryEventTypeRatherThanJustTheFirst(DataSource dataSource) {
            JdbcProfileAdvisorRepository repository = repository(dataSource);
            seed(repository);
            assertEquals(2, repository.findPrompts().size(), "two event types were seeded");

            repository.deleteAll();

            assertTrue(repository.findPrompts().isEmpty());
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
                    new AdvisorPromptRow(
                            CPU, "CPU", 2_000L, "the rebuilt user message", 8.0, HOT_METHOD, WHEN));

            assertEquals(1, repository.findPrompts().size());
            assertEquals(2_000L, repository.findPrompts().getFirst().samples());
        }
    }
}
