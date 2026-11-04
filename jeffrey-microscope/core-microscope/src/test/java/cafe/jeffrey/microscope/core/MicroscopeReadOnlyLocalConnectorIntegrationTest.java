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

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.RecordedSession;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microscope booted as it ships over a local directory declared {@code read-only}: it says so to the
 * agent and the UI, and the UI's delete reaches nothing on the volume.
 */
@SpringBootTest(classes = MicroscopeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MicroscopeReadOnlyLocalConnectorIntegrationTest {

    private static final String WORKSPACE = "uat";
    private static final String PROJECT = "jeffrey";
    private static final String POD = "jeffrey-0";

    /** Under the build directory, so {@code mvn clean} takes it with everything else a run leaves. */
    private static final Path RUN = Path.of("target", "local-connector-it", UUID.randomUUID().toString())
            .toAbsolutePath();
    private static final Path HOME = RUN.resolve("home");
    private static final Path VOLUME = RUN.resolve("volume");
    private static final Path SESSION;

    static {
        Path project = VolumeWriter.project(VOLUME.resolve(WORKSPACE), PROJECT);
        SESSION = RecordedSession.writeTo(VolumeWriter.instance(project, POD));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jeffrey.microscope.home.dir", HOME::toString);
        registry.add("jeffrey.microscope.connector.local.path", VOLUME::toString);
        registry.add("jeffrey.microscope.connector.local.read-only", () -> "true");
        registry.add("jeffrey.microscope.update-check.enabled", () -> "false");
        registry.add("jeffrey.microscope.mcp.ide.enabled", () -> "false");
    }

    @LocalServerPort
    int port;

    private MicroscopeClient microscope;

    @BeforeEach
    void connect() {
        microscope = new MicroscopeClient(port);
    }

    @Test
    void theAgentIsToldTheConnectorIsReadOnly() {
        JsonNode status = microscope.answer("connector_status", Json.createObject());

        assertEquals("REACHABLE", status.path("state").asString());
        assertTrue(status.path("readOnly").asBoolean());
    }

    @Test
    void theUiIsToldTheConnectorIsReadOnly() {
        assertTrue(microscope.get("/api/internal/connector").path("connector").path("readOnly").asBoolean());
    }

    @Test
    void aSessionDeleteIsRefusedAndTheSessionStays() {
        HttpResponse<String> response = microscope.delete("/api/internal/connector/workspaces/" + WORKSPACE
                + "/projects/" + PROJECT + "/repository/sessions/" + RecordedSession.SESSION_ID);

        assertTrue(response.statusCode() >= 400 && response.statusCode() < 500,
                () -> response.statusCode() + " " + response.body());
        assertTrue(response.body().contains("read-only"), response::body);
        assertTrue(Files.exists(SESSION.resolve(RecordedSession.CHUNKS.getFirst().volumeName())));
    }
}
