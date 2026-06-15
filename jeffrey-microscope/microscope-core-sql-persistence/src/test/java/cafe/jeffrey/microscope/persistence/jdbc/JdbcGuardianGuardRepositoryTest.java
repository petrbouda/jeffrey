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

import cafe.jeffrey.microscope.persistence.api.GuardianGroupSetting;
import cafe.jeffrey.microscope.persistence.api.GuardianGuard;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcGuardianGuardRepositoryTest {

    private static JdbcGuardianGuardRepository repository(DataSource dataSource) {
        return new JdbcGuardianGuardRepository(new DatabaseClientProvider(dataSource));
    }

    private static GuardianGuard customGuard(String id) {
        return new GuardianGuard(
                id, "Acme Overhead", true, false,
                "EXECUTION_SAMPLE", "APPLICATION", "SAMPLES", "JAVA", "FULL_MATCH",
                0.03, 0.05,
                "{\"anchor\":{\"type\":\"Predicate\",\"op\":\"PREFIX\",\"value\":\"com.acme.\"}}",
                null, "Acme activity", "explanation", "solution",
                Instant.parse("2026-06-15T10:00:00Z"));
    }

    @Nested
    class Seed {

        @Test
        void loadsAllBuiltInGuards(DataSource dataSource) {
            List<GuardianGuard> guards = repository(dataSource).findAll();

            // The V001 migration seeds every built-in guard (25 CPU-overhead guards mirrored across
            // EXECUTION_SAMPLE + CPU_TIME_SAMPLE, plus the allocation, wall-clock and blocking sets).
            assertEquals(73, guards.size());
            assertTrue(guards.stream().allMatch(GuardianGuard::builtIn), "all seeded guards are built-in");
            assertTrue(guards.stream().allMatch(GuardianGuard::enabled), "all seeded guards are enabled");
        }

        @Test
        void seedsKnownGuardWithParsedColumns(DataSource dataSource) {
            Map<String, GuardianGuard> byId = repository(dataSource).findAll().stream()
                    .collect(Collectors.toMap(GuardianGuard::guardId, Function.identity()));

            GuardianGuard logback = byId.get("exec-logback");
            assertEquals("Logback CPU Overhead", logback.name());
            assertEquals("EXECUTION_SAMPLE", logback.groupKind());
            assertEquals("APPLICATION", logback.category());
            assertTrue(logback.matcherSpec().contains("ch.qos.logback"));

            // GC guard carries async-profiler + GC-type preconditions and a descend traversal.
            GuardianGuard g1 = byId.get("exec-gc-g1");
            assertTrue(g1.preconditions().contains("G1"));
            assertTrue(g1.matcherSpec().contains("Descend"));
        }

        @Test
        void seedsGroupSampleGates(DataSource dataSource) {
            Map<String, Long> minSamples = repository(dataSource).findAllGroupSettings().stream()
                    .collect(Collectors.toMap(GuardianGroupSetting::groupKind, GuardianGroupSetting::minSamples));

            assertEquals(5, minSamples.size());
            assertEquals(1000L, minSamples.get("EXECUTION_SAMPLE"));
            assertEquals(100L, minSamples.get("BLOCKING"));
        }
    }

    @Nested
    class Crud {

        @Test
        void insertsAndFindsCustomGuard(DataSource dataSource) {
            JdbcGuardianGuardRepository repository = repository(dataSource);
            repository.insert(customGuard("custom-1"));

            Optional<GuardianGuard> found = repository.find("custom-1");
            assertTrue(found.isPresent());
            assertEquals("Acme Overhead", found.get().name());
            assertFalse(found.get().builtIn());
            assertEquals(0.05, found.get().warningThreshold());
        }

        @Test
        void updatesExistingGuard(DataSource dataSource) {
            JdbcGuardianGuardRepository repository = repository(dataSource);
            repository.insert(customGuard("custom-2"));

            GuardianGuard original = repository.find("custom-2").orElseThrow();
            GuardianGuard modified = new GuardianGuard(
                    original.guardId(), "Renamed", false, original.builtIn(), original.groupKind(),
                    original.category(), original.resultType(), original.targetFrame(), original.matchingType(),
                    0.1, 0.2, original.matcherSpec(), original.preconditions(), original.summaryNoun(),
                    original.explanation(), original.solution(), original.createdAt());
            repository.update(modified);

            GuardianGuard reloaded = repository.find("custom-2").orElseThrow();
            assertEquals("Renamed", reloaded.name());
            assertFalse(reloaded.enabled());
            assertEquals(0.2, reloaded.warningThreshold());
        }

        @Test
        void deletesGuard(DataSource dataSource) {
            JdbcGuardianGuardRepository repository = repository(dataSource);
            repository.insert(customGuard("custom-3"));

            repository.delete("custom-3");

            assertTrue(repository.find("custom-3").isEmpty());
        }
    }
}
