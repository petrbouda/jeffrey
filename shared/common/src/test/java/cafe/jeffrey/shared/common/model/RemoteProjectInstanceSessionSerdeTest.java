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

package cafe.jeffrey.shared.common.model;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettings;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forward/backward compatibility of the shared-filesystem JSON contract:
 * files written by an older provisioner/hub (missing the newer fields) must
 * deserialize, and files with the newer fields must round-trip.
 */
class RemoteProjectInstanceSessionSerdeTest {

    @Test
    void oldFormatSessionInfo_withoutProfilerFields_deserializes() {
        String oldFormat = """
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

        RemoteProjectInstanceSession session = Json.read(oldFormat, RemoteProjectInstanceSession.class);

        assertEquals("session-001", session.sessionId());
        assertEquals(1, session.order());
        assertNull(session.profilerSettingsSource());
        assertNull(session.profilerCommand());
    }

    @Test
    void newFormatSessionInfo_roundTrips() {
        RemoteProjectInstanceSession session = new RemoteProjectInstanceSession(
                "session-001", "proj-001", "ws-001", "inst-001",
                1700000000000L, 2, "inst-001/session-001",
                "HUB_PROJECT", "-agentpath:/lib.so=start");

        RemoteProjectInstanceSession read = Json.read(Json.toString(session), RemoteProjectInstanceSession.class);

        assertEquals(session, read);
    }

    @Test
    void oldFormatWorkspaceSettings_withoutIdKeyedMap_deserializes() {
        String oldFormat = """
                {
                    "profiler": {
                        "defaultSettings": "cmd",
                        "defaultSettingsLevel": "WORKSPACE",
                        "projectSettings": {"proj-name": "proj-cmd"}
                    }
                }
                """;

        RemoteWorkspaceSettings settings = Json.read(oldFormat, RemoteWorkspaceSettings.class);
        ProfilerSettings profiler = settings.profiler();

        assertEquals("cmd", profiler.defaultSettings());
        assertEquals("proj-cmd", profiler.projectSettings().get("proj-name"));
        assertNull(profiler.projectSettingsById());
    }
}
