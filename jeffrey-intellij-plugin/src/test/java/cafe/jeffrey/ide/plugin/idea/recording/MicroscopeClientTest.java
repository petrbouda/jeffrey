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

package cafe.jeffrey.ide.plugin.idea.recording;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The wire between the plugin and Microscope, driven against a real local server.
 *
 * <p>Worth a server rather than a mock because the failure this guards against is a malformed URL —
 * a query parameter dropped or double-encoded — which no amount of mocking the client can catch, and
 * which shows up in the IDE as a panel that quietly reports every recording as never analysed.
 */
public class MicroscopeClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private final List<String> paths = new ArrayList<>();

    private Path recording;

    @Before
    public void startServer() throws IOException {
        recording = Files.createTempFile("jeffrey-20260904-180108", ".jfr");
        Files.write(recording, new byte[1024]);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stopServer() throws IOException {
        server.stop(0);
        Files.deleteIfExists(recording);
    }

    @Test
    public void asksAboutAFileByItsAbsolutePathAndSize() {
        respond("/api/internal/recordings/by-path", 200, "{\"state\":\"IMPORTED\",\"recordingId\":\"rec-1\"}");

        RecordingState state = client().state(recording);

        assertEquals(RecordingState.Status.IMPORTED, state.status());
        assertEquals("rec-1", state.recordingId());
        assertTrue(lastRequest.get().contains("sizeInBytes=1024"));
        assertTrue(lastRequest.get().contains(encodedPath()));
    }

    /**
     * The panel's first paint depends on this answer, so a Microscope that is not running has to come
     * back as a state the panel draws, never as an exception thrown into the editor.
     */
    @Test
    public void reportsAnUnreachableMicroscopeAsAState() throws IOException {
        server.stop(0);

        RecordingState state = client().state(recording);

        assertEquals(RecordingState.Status.UNAVAILABLE, state.status());
        assertEquals(recording.getFileName().toString(), state.filename());
        assertEquals(1024L, state.sizeInBytes());
        assertNull(state.summary());
    }

    @Test
    public void reportsAnErrorStatusAsUnavailableRatherThanGuessing() {
        respond("/api/internal/recordings/by-path", 500, "{\"message\":\"boom\"}");

        assertEquals(RecordingState.Status.UNAVAILABLE, client().state(recording).status());
    }

    @Test
    public void importsThenAnalysesInThatOrder() throws Exception {
        respond("/api/internal/recordings/from-path", 200, "{\"recordingId\":\"rec-1\"}");
        respond("/api/internal/recordings/recordings/rec-1/analyze", 200, "{\"profileId\":\"profile-1\"}");

        assertEquals("profile-1", client().analyze(recording));
        assertEquals(
                List.of("/api/internal/recordings/from-path",
                        "/api/internal/recordings/recordings/rec-1/analyze"),
                paths);
    }

    /**
     * Unlike the state query this one runs behind a button, so a failure has somewhere to be
     * reported and must not be swallowed into a panel that claims nothing happened.
     */
    @Test
    public void throwsWhenTheImportIsRejected() {
        respond("/api/internal/recordings/from-path", 400, "{\"message\":\"Unsupported recording file type\"}");

        IOException failure = assertThrows(IOException.class, () -> client().analyze(recording));
        assertTrue(failure.getMessage().contains("400"));
        assertTrue(failure.getMessage().contains("Unsupported recording file type"));
    }

    @Test
    public void throwsWhenTheAnalysisAnswersWithoutAProfileId() {
        respond("/api/internal/recordings/from-path", 200, "{\"recordingId\":\"rec-1\"}");
        respond("/api/internal/recordings/recordings/rec-1/analyze", 200, "{}");

        assertNotNull(assertThrows(IOException.class, () -> client().analyze(recording)).getMessage());
    }

    @Test
    public void buildsProfileAndViewUrls() {
        MicroscopeClient client = new MicroscopeClient(baseUrl + "/");

        assertEquals(baseUrl + "/profiles/profile-1", client.profileUrl("profile-1"));
        assertEquals(
                baseUrl + "/profiles/profile-1/flamegraphs/primary",
                client.viewUrl("profile-1", ProfileView.ALL.getFirst().path()));
    }

    private MicroscopeClient client() {
        return new MicroscopeClient(baseUrl);
    }

    private String encodedPath() {
        return recording.toAbsolutePath().toString().replace("/", "%2F");
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            record(exchange);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private void record(HttpExchange exchange) {
        paths.add(exchange.getRequestURI().getPath());
        lastRequest.set(exchange.getRequestURI().toString());
    }
}
