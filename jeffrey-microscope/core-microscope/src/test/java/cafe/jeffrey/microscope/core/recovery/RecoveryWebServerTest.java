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

package cafe.jeffrey.microscope.core.recovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryWebServerTest {

    private static final Path CURRENT_HOME = Path.of("/current/home");
    private static final Path FRESH_HOME = Path.of("/fresh/home");
    private static final Path SWITCHED_HOME = Path.of("/switched/home");
    private static final String INVALID_PATH_MESSAGE = "The path must be absolute.";

    private RecoveryWebServer server;
    private HttpClient client;

    @BeforeEach
    void startServer() throws Exception {
        server = new RecoveryWebServer(
                0,
                CURRENT_HOME,
                () -> FRESH_HOME,
                rawPath -> {
                    if (!rawPath.startsWith("/")) {
                        throw new IllegalArgumentException(INVALID_PATH_MESSAGE);
                    }
                    return SWITCHED_HOME;
                });
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.close();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesRecoveryPageForAnyNonApiPath() throws Exception {
        HttpResponse<String> root = get("/");
        HttpResponse<String> deepLink = get("/projects/detail");

        assertEquals(200, root.statusCode());
        assertTrue(root.body().contains("jeffrey-recovery"));
        assertEquals(200, deepLink.statusCode());
        assertTrue(deepLink.body().contains("jeffrey-recovery"));
    }

    @Test
    void unknownApiPathIsNotFound() throws Exception {
        assertEquals(404, get("/api/internal/projects").statusCode());
    }

    @Test
    void statusExposesOnlyTheHomeDirectory() throws Exception {
        HttpResponse<String> response = get("/api/recovery/status");

        assertEquals(200, response.statusCode());
        assertEquals("{\"homeDir\":\"" + CURRENT_HOME + "\"}", response.body());
    }

    @Test
    void startFreshCompletesWithCurrentHome() throws Exception {
        HttpResponse<String> response = post("/api/recovery/start-fresh", "");

        assertEquals(200, response.statusCode());
        assertEquals(FRESH_HOME, server.awaitAction());
    }

    @Test
    void switchHomeCompletesWithNewHome() throws Exception {
        HttpResponse<String> response = post("/api/recovery/switch-home", "{\"path\":\"/new/home\"}");

        assertEquals(200, response.statusCode());
        assertEquals(SWITCHED_HOME, server.awaitAction());
    }

    @Test
    void invalidSwitchHomePathIsRejectedWithInlineError() throws Exception {
        HttpResponse<String> response = post("/api/recovery/switch-home", "{\"path\":\"relative\"}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains(INVALID_PATH_MESSAGE));
    }

    @Test
    void actionsRejectNonPostRequests() throws Exception {
        assertEquals(405, get("/api/recovery/start-fresh").statusCode());
        assertEquals(405, get("/api/recovery/switch-home").statusCode());
    }
}
