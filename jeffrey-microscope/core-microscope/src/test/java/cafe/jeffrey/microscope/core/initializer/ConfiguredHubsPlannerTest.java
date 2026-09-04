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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.DesiredHub;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredHubsPlannerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:15:30Z");
    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private final ConfiguredHubsPlanner planner =
            new ConfiguredHubsPlanner(Clock.fixed(NOW, ZoneOffset.UTC));

    private static DesiredHub desired(String key, String hostname, int port) {
        return new DesiredHub(key, "cfg-" + key, key, new HubAddress(hostname, port));
    }

    private static HubInfo stored(String hubId, String name, String hostname, int port, HubSource source) {
        return new HubInfo(hubId, name, new HubAddress(hostname, port), EARLIER, source);
    }

    @Nested
    @DisplayName("A hub that is not stored yet")
    class NewHub {

        @Test
        void isInserted() {
            HubReconcilePlan plan = planner.plan(
                    List.of(desired("prod", "hub.example.com", 443)), List.of());

            assertEquals(1, plan.inserts().size());
            assertTrue(plan.updates().isEmpty());
            assertTrue(plan.deletes().isEmpty());

            HubInfo inserted = plan.inserts().getFirst();
            assertEquals("cfg-prod", inserted.hubId());
            assertEquals(HubSource.CONFIG, inserted.source());
            assertEquals(NOW, inserted.createdAt());
        }
    }

    @Nested
    @DisplayName("A hub that already matches its declaration")
    class UnchangedHub {

        @Test
        void produces_an_empty_plan() {
            HubReconcilePlan plan = planner.plan(
                    List.of(desired("prod", "hub.example.com", 443)),
                    List.of(stored("cfg-prod", "prod", "hub.example.com", 443, HubSource.CONFIG)));

            assertTrue(plan.isEmpty());
        }
    }

    @Nested
    @DisplayName("A declaration that changed")
    class ChangedHub {

        @Test
        void renaming_updates_in_place_and_keeps_the_creation_time() {
            DesiredHub renamed = new DesiredHub(
                    "prod", "cfg-prod", "Production", new HubAddress("hub.example.com", 443));

            HubReconcilePlan plan = planner.plan(
                    List.of(renamed),
                    List.of(stored("cfg-prod", "prod", "hub.example.com", 443, HubSource.CONFIG)));

            assertEquals(1, plan.updates().size());
            assertTrue(plan.inserts().isEmpty());
            assertTrue(plan.deletes().isEmpty());

            HubInfo target = plan.updates().getFirst().target();
            assertEquals("Production", target.name());
            assertEquals("cfg-prod", target.hubId());
            assertEquals(EARLIER, target.createdAt());
        }

        @Test
        void moving_to_a_new_address_recreates_the_row_keeping_its_id_and_creation_time() {
            HubReconcilePlan plan = planner.plan(
                    List.of(desired("prod", "moved.example.com", 443)),
                    List.of(stored("cfg-prod", "prod", "hub.example.com", 443, HubSource.CONFIG)));

            // Delete-plus-insert rather than an update, so two hubs swapping addresses in one edit
            // never collide on UNIQUE (hostname, port).
            assertEquals(1, plan.deletes().size());
            assertEquals(1, plan.inserts().size());
            assertTrue(plan.updates().isEmpty());

            assertEquals("hub.example.com", plan.deletes().getFirst().address().hostname());

            HubInfo inserted = plan.inserts().getFirst();
            assertEquals("cfg-prod", inserted.hubId());
            assertEquals("moved.example.com", inserted.address().hostname());
            assertEquals(EARLIER, inserted.createdAt());
        }

        @Test
        void two_hubs_can_exchange_addresses() {
            HubReconcilePlan plan = planner.plan(
                    List.of(desired("a", "host-b", 443), desired("b", "host-a", 443)),
                    List.of(
                            stored("cfg-a", "a", "host-a", 443, HubSource.CONFIG),
                            stored("cfg-b", "b", "host-b", 443, HubSource.CONFIG)));

            // Both rows must be gone before either is written back.
            assertEquals(2, plan.deletes().size());
            assertEquals(2, plan.inserts().size());
            assertTrue(plan.updates().isEmpty());
        }
    }

    @Nested
    @DisplayName("A hub dropped from the configuration")
    class RetiredHub {

        @Test
        void is_deleted_when_it_was_configuration_owned() {
            HubReconcilePlan plan = planner.plan(
                    List.of(),
                    List.of(stored("cfg-old", "old", "old.example.com", 443, HubSource.CONFIG)));

            assertEquals(1, plan.deletes().size());
            assertEquals("cfg-old", plan.deletes().getFirst().hubId());
        }

        @Test
        void a_user_added_hub_is_never_swept() {
            HubInfo userHub = stored("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER);

            HubReconcilePlan plan = planner.plan(List.of(), List.of(userHub));

            assertTrue(plan.isEmpty());
        }
    }

    @Nested
    @DisplayName("A user-added hub at a declared address")
    class AdoptedHub {

        private final DesiredHub declaration = desired("prod", "mine.example.com", 9090);
        private final HubInfo userHub =
                stored("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER);

        @Test
        void is_adopted_in_place_keeping_its_id_and_creation_time() {
            HubReconcilePlan plan = planner.plan(List.of(declaration), List.of(userHub));

            assertEquals(1, plan.updates().size());
            assertTrue(plan.inserts().isEmpty());
            assertTrue(plan.deletes().isEmpty());

            HubInfo target = plan.updates().getFirst().target();
            assertEquals("uuid-1", target.hubId());
            assertEquals(EARLIER, target.createdAt());
            assertEquals(HubSource.CONFIG, target.source());
            assertEquals("prod", target.name());
        }

        @Test
        void stays_adopted_on_the_next_run() {
            HubInfo adopted = planner.plan(List.of(declaration), List.of(userHub))
                    .updates().getFirst().target();

            // The row kept its generated id rather than taking cfg-prod, so the second run has to
            // re-match it by address -- otherwise the reconcile would flip it on alternating starts.
            HubReconcilePlan second = planner.plan(List.of(declaration), List.of(adopted));

            assertTrue(second.isEmpty());
        }
    }

    @Nested
    @DisplayName("An address claimed by configuration but held by another row")
    class ContestedAddress {

        @Test
        void evicts_the_user_added_occupant() {
            HubInfo occupant = stored("uuid-1", "Mine", "contested", 9090, HubSource.USER);
            HubInfo configured = stored("cfg-prod", "prod", "elsewhere", 9090, HubSource.CONFIG);

            HubReconcilePlan plan = planner.plan(
                    List.of(desired("prod", "contested", 9090)), List.of(configured, occupant));

            assertTrue(plan.deletes().stream().anyMatch(hub -> hub.hubId().equals("uuid-1")));
            assertTrue(plan.inserts().stream().anyMatch(hub -> hub.hubId().equals("cfg-prod")));
        }
    }

    @Nested
    @DisplayName("Hubs declared alongside a user-added one")
    class MixedRegistry {

        @Test
        void leaves_the_user_added_hub_untouched() {
            HubInfo userHub = stored("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER);

            HubReconcilePlan plan = planner.plan(
                    List.of(desired("prod", "hub.example.com", 443)), List.of(userHub));

            assertEquals(1, plan.inserts().size());
            assertTrue(plan.deletes().isEmpty());
            assertTrue(plan.updates().isEmpty());
            assertSame(HubSource.USER, userHub.source());
        }
    }
}
