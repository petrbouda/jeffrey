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

import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forward/backward compatibility of the shared-filesystem JSON contract: files written by an
 * older provisioner (with fields since dropped, or without newer ones) must deserialize, and
 * files the current provisioner writes must round-trip.
 */
class RemoteProjectInstanceSessionSerdeTest {

    @Test
    void olderSessionInfo_withFieldsSinceDropped_deserializes() {
        String olderFormat = """
                {
                    "sessionId": "session-001",
                    "projectId": "proj-001",
                    "workspaceId": "ws-001",
                    "instanceId": "inst-001",
                    "createdAt": 1700000000000,
                    "order": 1,
                    "relativeSessionPath": "inst-001/session-001",
                    "profilerSettingsSource": "CLI_CONFIG",
                    "profilerCommand": "-agentpath:/lib.so=start",
                    "heartbeatExpected": true
                }
                """;

        RemoteProjectInstanceSession session = Json.read(olderFormat, RemoteProjectInstanceSession.class);

        assertEquals(new RemoteProjectInstanceSession(
                "session-001", "inst-001", 1700000000000L, 1, "inst-001/session-001", true), session);
    }

    @Test
    void sessionInfo_withoutHeartbeatDeclaration_isUnknownRatherThanFalse() {
        String withoutHeartbeatField = """
                {
                    "sessionId": "session-001",
                    "instanceId": "inst-001",
                    "createdAt": 1700000000000,
                    "order": 1,
                    "relativeSessionPath": "inst-001/session-001"
                }
                """;

        RemoteProjectInstanceSession session =
                Json.read(withoutHeartbeatField, RemoteProjectInstanceSession.class);

        // Not merely absent: null is what tells the hub this session never declared whether
        // anything would report liveness, so it must not be finished for failing to report it
        assertNull(session.heartbeatExpected());
    }

    @Test
    void sessionInfo_declaringNoHeartbeat_roundTripsAsFalse() {
        RemoteProjectInstanceSession session = new RemoteProjectInstanceSession(
                "session-001", "inst-001", 1700000000000L, 1, "inst-001/session-001", false);

        RemoteProjectInstanceSession read = Json.read(Json.toString(session), RemoteProjectInstanceSession.class);

        assertEquals(Boolean.FALSE, read.heartbeatExpected());
        assertEquals(session, read);
    }

    @Test
    void currentSessionInfo_roundTrips() {
        RemoteProjectInstanceSession session = new RemoteProjectInstanceSession(
                "session-001", "inst-001", 1700000000000L, 2, "inst-001/session-001", true);

        RemoteProjectInstanceSession read = Json.read(Json.toString(session), RemoteProjectInstanceSession.class);

        assertEquals(session, read);
    }
}
