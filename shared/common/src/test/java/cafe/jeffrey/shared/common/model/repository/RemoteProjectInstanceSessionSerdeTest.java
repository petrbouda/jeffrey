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


package cafe.jeffrey.shared.common.model.repository;

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.config.ConfigScope;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The marker files on the shared volume are a contract between two processes that upgrade
 * independently, so both directions matter: a file written by an older provisioner must still be
 * readable, and a file carrying fields this version has never heard of must not become unreadable.
 */
class RemoteProjectInstanceSessionSerdeTest {

    @Nested
    class ReadingOlderFiles {

        @Test
        void aFileWithoutTheProfilerFieldsDeserializes() {
            String olderFormat = """
                    {
                        "sessionId": "session-001",
                        "projectId": "proj-001",
                        "workspaceId": "ws-001",
                        "instanceId": "inst-001",
                        "createdAt": 1700000000000,
                        "order": 1,
                        "relativeSessionPath": "inst-001/session-001"
                    }
                    """;

            RemoteProjectInstanceSession session = Json.read(olderFormat, RemoteProjectInstanceSession.class);

            assertEquals("session-001", session.sessionId());
            assertEquals(1, session.order());
            assertNull(session.profilerCommandSource());
            assertNull(session.profilerCommand());
            assertTrue(session.configLayers().isEmpty());
        }

        @Test
        void aFileWithoutAHeartbeatDeclarationIsUnknownRatherThanFalse() {
            String olderFormat = """
                    {
                        "sessionId": "session-001",
                        "projectId": "proj-001",
                        "workspaceId": "ws-001",
                        "instanceId": "inst-001",
                        "createdAt": 1700000000000,
                        "order": 1,
                        "relativeSessionPath": "inst-001/session-001"
                    }
                    """;

            RemoteProjectInstanceSession session = Json.read(olderFormat, RemoteProjectInstanceSession.class);

            // Not merely absent: null is what tells the hub this session never declared whether
            // anything would report liveness, so it must not be finished for failing to report it
            assertNull(session.heartbeatExpected());
        }

        @Test
        void aFileCarryingAFieldThisVersionDoesNotKnowIsStillReadable() {
            String fromANewerWriter = """
                    {
                        "sessionId": "session-001",
                        "projectId": "proj-001",
                        "workspaceId": "ws-001",
                        "instanceId": "inst-001",
                        "createdAt": 1700000000000,
                        "order": 1,
                        "relativeSessionPath": "inst-001/session-001",
                        "somethingAddedLater": "value"
                    }
                    """;

            RemoteProjectInstanceSession session = Json.read(fromANewerWriter, RemoteProjectInstanceSession.class);

            assertEquals("session-001", session.sessionId());
        }
    }

    @Nested
    class RoundTrips {

        @Test
        void aSessionDeclaringNoHeartbeatRoundTripsAsFalse() {
            RemoteProjectInstanceSession session = session(false, List.of());

            RemoteProjectInstanceSession read =
                    Json.read(Json.toString(session), RemoteProjectInstanceSession.class);

            assertEquals(Boolean.FALSE, read.heartbeatExpected());
        }

        @Test
        void appliedConfigLayersRoundTrip() {
            RemoteProjectInstanceSession session = session(true, List.of(
                    new AppliedConfigLayer(ConfigScope.WORKSPACE, "aaa"),
                    new AppliedConfigLayer(ConfigScope.PROJECT, "bbb")));

            RemoteProjectInstanceSession read =
                    Json.read(Json.toString(session), RemoteProjectInstanceSession.class);

            assertEquals(session, read);
            assertEquals(ConfigScope.PROJECT, read.configLayers().get(1).scope());
            assertEquals("bbb", read.configLayers().get(1).digest());
        }

        @Test
        void missingConfigLayersBecomeAnEmptyListRatherThanNull() {
            RemoteProjectInstanceSession session = session(true, null);

            assertTrue(session.configLayers().isEmpty());
            assertTrue(Json.read(Json.toString(session), RemoteProjectInstanceSession.class)
                    .configLayers().isEmpty());
        }
    }

    private static RemoteProjectInstanceSession session(
            boolean heartbeatExpected, List<AppliedConfigLayer> layers) {

        return new RemoteProjectInstanceSession(
                "session-001",
                "proj-001",
                "ws-001",
                "inst-001",
                1700000000000L,
                1,
                "inst-001/session-001",
                "HUB_WORKSPACE",
                "-agentpath:/opt/libasyncProfiler.so=start,cpu",
                heartbeatExpected,
                layers);
    }
}
