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

package cafe.jeffrey.microscope.grpc.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaySubscriptionRequestTest {

    private static final String SESSION_ID = "session-1";
    private static final Set<String> EVENT_TYPES = Set.of("jdk.CPULoad");
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String PROJECT_ID = "project-1";

    @Nested
    class Session {

        @Test
        void rejectsNullSessionId() {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(null, EVENT_TYPES, null, null));
            assertEquals("sessionId is required", error.getMessage());
        }

        @Test
        void rejectsBlankSessionId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest("   ", EVENT_TYPES, null, null));
        }
    }

    @Nested
    class EventTypes {

        @Test
        void rejectsNullEventTypes() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, null, null, null));
        }

        @Test
        void rejectsBlankEventType() {
            Set<String> withBlank = new HashSet<>();
            withBlank.add("jdk.CPULoad");
            withBlank.add(" ");
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, withBlank, null, null));
        }

        @Test
        void acceptsEmptyEventTypesAndLeavesTheRefusalToTheHub() {
            var request = new ReplaySubscriptionRequest(SESSION_ID, Set.of(), null, null);
            assertTrue(request.eventTypes().isEmpty());
        }

        @Test
        void copiesEventTypesSoALaterEditToTheCallersSetDoesNotReachTheRequest() {
            Set<String> mutable = new HashSet<>(EVENT_TYPES);
            var request = new ReplaySubscriptionRequest(SESSION_ID, mutable, null, null);
            mutable.add("jdk.GarbageCollection");
            assertEquals(EVENT_TYPES, request.eventTypes());
        }
    }

    @Nested
    class Window {

        @Test
        void rejectsInvertedWindow() {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, 2_000L, 1_000L));
            assertEquals("startTime must be strictly before endTime", error.getMessage());
        }

        @Test
        void rejectsEmptyWindow() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, 1_000L, 1_000L));
        }

        @Test
        void acceptsOrderedWindow() {
            var request = new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, 1_000L, 2_000L);
            assertEquals(1_000L, request.startTime());
            assertEquals(2_000L, request.endTime());
        }

        @Test
        void acceptsHalfOpenWindows() {
            new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, 1_000L, null);
            new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, 2_000L);
        }
    }

    @Nested
    class Scope {

        @Test
        void unscopedRequestGoesOverTheLegacyRpc() {
            var request = new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, null);
            assertFalse(request.scoped());
        }

        @Test
        void scopedRequestCarriesBothIds() {
            var request = new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, null, WORKSPACE_ID, PROJECT_ID);
            assertTrue(request.scoped());
            assertEquals(WORKSPACE_ID, request.workspaceId());
            assertEquals(PROJECT_ID, request.projectId());
        }

        @Test
        void rejectsWorkspaceWithoutProject() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, null, WORKSPACE_ID, null));
        }

        @Test
        void rejectsProjectWithoutWorkspace() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, null, null, PROJECT_ID));
        }

        @Test
        void rejectsBlankScopeIds() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, null, " ", PROJECT_ID));
            assertThrows(IllegalArgumentException.class,
                    () -> new ReplaySubscriptionRequest(SESSION_ID, EVENT_TYPES, null, null, WORKSPACE_ID, ""));
        }
    }
}
