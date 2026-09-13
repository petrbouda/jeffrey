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

import cafe.jeffrey.hub.api.v1.ActivityScope;
import cafe.jeffrey.hub.api.v1.ActivityState;
import cafe.jeffrey.hub.api.v1.EventActivitySnapshot;
import cafe.jeffrey.shared.common.activity.ActivityLimits;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityCodecTest {

    private static final String KEY = "a".repeat(64);

    private static ActivityScanRequest request(String idempotencyKey) {
        return new ActivityScanRequest(
                "workspace", "project", "session", 1000, 121000, 60, Set.of("jdk.GarbageCollection"), idempotencyKey);
    }

    @Nested
    class StartRequests {

        @Test
        void carriesTheIdempotencyKeyOnTheWire() {
            var wire = ActivityCodec.start(request(KEY));

            assertEquals(KEY, wire.getIdempotencyKey());
            assertEquals("workspace", wire.getScope().getWorkspaceId());
            assertEquals("project", wire.getScope().getProjectId());
            assertEquals("session", wire.getScope().getSessionId());
            assertEquals(1000, wire.getStartTime());
            assertEquals(121000, wire.getEndTime());
            assertEquals(60, wire.getBucketSeconds());
            assertEquals(List.of("jdk.GarbageCollection"), wire.getEventTypesList());
        }

        @Test
        void aMissingKeyLeavesTheWireFieldEmpty() {
            assertEquals("", ActivityCodec.start(request(null)).getIdempotencyKey());
            assertEquals("", ActivityCodec.start(request("  ")).getIdempotencyKey());
        }

        @Test
        void aBlankKeyReadsAsNoneAndAnOversizedOneIsRefused() {
            assertNull(request("  ").idempotencyKey());
            assertFalse(request(null).hasIdempotencyKey());
            assertTrue(request(KEY).hasIdempotencyKey());
            assertThrows(IllegalArgumentException.class,
                    () -> request("k".repeat(ActivityLimits.MAX_IDEMPOTENCY_KEY_LENGTH + 1)));
        }
    }

    @Nested
    class Snapshots {

        /** The wire field is {@code requested_event_types}, and the record calls it the same thing. */
        @Test
        void requestedEventTypesKeepTheirWireName() {
            var wire = EventActivitySnapshot.newBuilder()
                    .setScanId("scan")
                    .setScope(ActivityScope.newBuilder()
                            .setWorkspaceId("workspace")
                            .setProjectId("project")
                            .setSessionId("session"))
                    .setState(ActivityState.ACTIVITY_STATE_RUNNING)
                    .setStartTime(1000)
                    .setEndTime(121000)
                    .setBucketMillis(60000)
                    .addRequestedEventTypes("jdk.GarbageCollection")
                    .addRequestedEventTypes("jdk.ThreadPark")
                    .build();

            var snapshot = ActivityCodec.snapshot(wire);

            assertEquals(List.of("jdk.GarbageCollection", "jdk.ThreadPark"), snapshot.requestedEventTypes());
            assertEquals(snapshot.requestedEventTypes(), snapshot.failedAt(2000, "gone").requestedEventTypes());
            assertTrue(snapshot.describes(new ActivityScanTarget("workspace", "project", "session", "scan")));
        }
    }
}
