/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.microscope.core.mcp.tools.connector.ConnectorSessionRef;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Microscope booted as it ships — every bean, the real DuckDB, the MCP endpoint and the UI's REST
 * API on a real port — over a local connector. A subclass lays down its volume and declares it with
 * {@link #declare}; every test class of one subclass declares the same configuration, so Spring
 * boots that Microscope once for all of them.
 */
@SpringBootTest(classes = MicroscopeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BootedMicroscope {

    static final String CONNECTOR_STATUS = "connector_status";
    static final String CONNECTOR_SESSIONS = "connector_sessions";
    static final String CONNECTOR_DOWNLOAD = "connector_download";
    static final String ANALYZE_RECORDING = "recordings_analyzeRecording";
    static final String PROFILES_SUMMARY = "profiles_summary";

    @LocalServerPort
    int port;

    MicroscopeClient microscope;

    @BeforeEach
    void connect() {
        microscope = new MicroscopeClient(port);
    }

    /**
     * The configuration a booted Microscope reads: its home, the volume its local connector reads,
     * and nothing that reaches beyond the machine (no release check, no IDE).
     */
    static void declare(DynamicPropertyRegistry registry, Path home, Path volume) {
        registry.add("jeffrey.microscope.home.dir", home::toString);
        registry.add("jeffrey.microscope.connector.local.path", volume::toString);
        registry.add("jeffrey.microscope.update-check.enabled", () -> "false");
        registry.add("jeffrey.microscope.mcp.ide.enabled", () -> "false");
    }

    /** A part of a session, downloaded; it must arrive. */
    JsonNode download(String sessionRef, ObjectNode window) {
        JsonNode download = microscope.answer(CONNECTOR_DOWNLOAD, window.put("sessionRef", sessionRef));
        assertEquals("DOWNLOADED", download.path("status").asString(), download::toString);
        return download;
    }

    /** The profile of what a download brought in, built now or reused. */
    String profileOf(JsonNode download) {
        JsonNode analyzed = microscope.answer(ANALYZE_RECORDING,
                Json.createObject().put("recordingId", download.path("recordingId").asString()));
        assertEquals("READY", analyzed.path("status").asString(), analyzed::toString);
        return analyzed.path("profileId").asString();
    }

    /** How many samples of one event type a profile holds, as its summary reports them. */
    int samples(String profileId, String eventType) {
        JsonNode summary = microscope.answer(PROFILES_SUMMARY, Json.createObject().put("profileId", profileId));
        for (JsonNode type : summary.path("eventTypes")) {
            if (eventType.equals(type.path("name").asString())) {
                return type.path("samples").asInt();
            }
        }
        throw new AssertionError("The profile holds no " + eventType + ": " + summary);
    }

    /** A session's ref, found among the connector's sessions. */
    String sessionRef(String sessionId) {
        return sessionRow(Json.createObject(), sessionId).path("sessionRef").asString();
    }

    /** A row names its session only inside its opaque ref, so it is found by decoding each one. */
    JsonNode sessionRow(ObjectNode filters, String sessionId) {
        JsonNode page = microscope.answer(CONNECTOR_SESSIONS, filters);
        for (JsonNode row : page.path("sessions")) {
            if (sessionIdOf(row).equals(sessionId)) {
                return row;
            }
        }
        throw new AssertionError("No session " + sessionId + " on the connector: " + page);
    }

    static List<String> sessionIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        for (JsonNode row : page.path("sessions")) {
            ids.add(sessionIdOf(row));
        }
        return ids;
    }

    static String sessionIdOf(JsonNode row) {
        return ConnectorSessionRef.decode(row.path("sessionRef").asString()).sessionId();
    }

    static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
