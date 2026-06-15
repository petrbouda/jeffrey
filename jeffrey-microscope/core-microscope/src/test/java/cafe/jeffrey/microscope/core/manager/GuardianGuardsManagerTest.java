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

package cafe.jeffrey.microscope.core.manager;

import cafe.jeffrey.microscope.persistence.api.GuardianGroupSetting;
import cafe.jeffrey.microscope.persistence.api.GuardianGuard;
import cafe.jeffrey.microscope.persistence.api.GuardianGuardRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianGuardsManagerTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final GuardianGuardsManager manager =
            new GuardianGuardsManager(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private static GuardianGuard draft(String name) {
        return new GuardianGuard(
                null, name, true, false,
                "EXECUTION_SAMPLE", "APPLICATION", "SAMPLES", "JAVA", "FULL_MATCH",
                0.03, 0.05,
                "{\"anchor\":{\"type\":\"Predicate\",\"op\":\"PREFIX\",\"value\":\"com.acme.\"}}",
                null, "Acme activity", "explanation", "solution", null);
    }

    @Test
    void createAssignsIdTimestampAndMarksCustom() {
        GuardianGuard created = manager.create(draft("Acme"));

        assertNotNull(created.guardId());
        assertFalse(created.guardId().isBlank());
        assertFalse(created.builtIn());
        assertEquals(NOW, created.createdAt());
        assertTrue(repository.find(created.guardId()).isPresent());
    }

    @Test
    void updatePreservesBuiltInFlagAndCreatedAt() {
        // Seed a built-in guard directly, as the migration would.
        Instant originalCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
        repository.insert(new GuardianGuard(
                "exec-logback", "Logback CPU Overhead", true, true,
                "EXECUTION_SAMPLE", "APPLICATION", "SAMPLES", "JAVA", "FULL_MATCH",
                0.02, 0.03, "{\"anchor\":{\"type\":\"Predicate\",\"op\":\"PREFIX\",\"value\":\"ch.qos.logback\"}}",
                null, "the logging", "e", "s", originalCreatedAt));

        Optional<GuardianGuard> updated = manager.update("exec-logback", draft("Edited"));

        assertTrue(updated.isPresent());
        assertEquals("Edited", updated.get().name());
        assertTrue(updated.get().builtIn(), "built-in flag is preserved across edits");
        assertEquals(originalCreatedAt, updated.get().createdAt(), "original timestamp is preserved");
    }

    @Test
    void updateMissingReturnsEmpty() {
        assertTrue(manager.update("does-not-exist", draft("X")).isEmpty());
    }

    @Test
    void deleteReportsWhetherGuardExisted() {
        GuardianGuard created = manager.create(draft("Acme"));

        assertTrue(manager.delete(created.guardId()));
        assertFalse(manager.delete(created.guardId()));
    }

    private static final class InMemoryRepository implements GuardianGuardRepository {
        private final Map<String, GuardianGuard> store = new LinkedHashMap<>();

        @Override
        public List<GuardianGuard> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Optional<GuardianGuard> find(String guardId) {
            return Optional.ofNullable(store.get(guardId));
        }

        @Override
        public void insert(GuardianGuard guard) {
            store.put(guard.guardId(), guard);
        }

        @Override
        public void update(GuardianGuard guard) {
            store.put(guard.guardId(), guard);
        }

        @Override
        public void delete(String guardId) {
            store.remove(guardId);
        }

        @Override
        public List<GuardianGroupSetting> findAllGroupSettings() {
            return List.of();
        }
    }
}
