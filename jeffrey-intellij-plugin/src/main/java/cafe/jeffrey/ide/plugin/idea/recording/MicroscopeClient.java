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

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The plugin's side of the conversation with Microscope.
 *
 * <p>Three calls, in the order the panel makes them: ask what Microscope holds for a file, import it,
 * analyse it. Importing and analysing stay separate because they are separately slow and separately
 * able to fail — an import that succeeded and an analysis that did not is a state the panel has to be
 * able to describe.
 *
 * <p>No IntelliJ types beyond the logger, so the wire handling can be tested against a plain
 * {@code HttpServer}. Every method blocks; callers run them off the EDT.
 */
public final class MicroscopeClient {

    private static final Logger LOG = Logger.getInstance(MicroscopeClient.class);

    private static final String BY_PATH = "/api/internal/recordings/by-path";
    private static final String FROM_PATH = "/api/internal/recordings/from-path";
    private static final String RECORDINGS = "/api/internal/recordings/recordings/";
    private static final String ANALYZE = "/analyze";
    private static final String PROFILES = "/profiles/";

    private static final String RECORDING_ID_FIELD = "recordingId";
    private static final String PROFILE_ID_FIELD = "profileId";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Short, because the panel blocks its first paint on it and a Microscope that is not running has
     * to read as "not running" rather than as a hang.
     */
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Long, because this one parses the recording. A multi-gigabyte JFR takes minutes, and a timeout
     * here would abandon work Microscope goes on to finish.
     */
    private static final Duration ANALYZE_TIMEOUT = Duration.ofHours(1);

    private static final int HTTP_OK_MIN = 200;
    private static final int HTTP_OK_MAX = 299;

    private final HttpClient httpClient;
    private final String baseUrl;

    public MicroscopeClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * What Microscope holds for this file. Never throws: the panel's whole first paint depends on
     * this answer, and "Microscope is not reachable" is a state it draws rather than an error it
     * reports.
     */
    public RecordingState state(Path file) {
        String filename = file.getFileName().toString();
        long size = sizeOf(file);
        String url = baseUrl + BY_PATH
                + "?path=" + encode(file.toAbsolutePath().toString())
                + "&sizeInBytes=" + size;

        try {
            HttpResponse<String> response = send(get(url, QUERY_TIMEOUT));
            if (!isSuccess(response)) {
                LOG.info("Microscope rejected a recording state query: status=" + response.statusCode()
                        + " file=" + filename);
                return RecordingState.unavailable(filename, size);
            }
            return MicroscopeJson.parseState(response.body(), filename, size);
        } catch (IOException | RuntimeException e) {
            LOG.info("Microscope is not reachable for a recording state query: file=" + filename, e);
            return RecordingState.unavailable(filename, size);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RecordingState.unavailable(filename, size);
        }
    }

    /**
     * Imports the file and builds its profile, returning the profile id.
     *
     * <p>Unlike {@link #state(Path)} this one throws, because it runs behind a button the developer
     * pressed: a failure has somewhere to be reported, and swallowing it would leave the panel
     * claiming nothing happened.
     */
    public String analyze(Path file) throws IOException, InterruptedException {
        String recordingId = importFromPath(file);
        return analyzeRecording(recordingId);
    }

    public String importFromPath(Path file) throws IOException, InterruptedException {
        String body = "{\"path\":" + quote(file.toAbsolutePath().toString()) + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + FROM_PATH))
                .timeout(QUERY_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return requireId(send(request), RECORDING_ID_FIELD);
    }

    public String analyzeRecording(String recordingId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + RECORDINGS + encode(recordingId) + ANALYZE))
                .timeout(ANALYZE_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return requireId(send(request), PROFILE_ID_FIELD);
    }

    /** The Microscope page for a profile. */
    public String profileUrl(String profileId) {
        return baseUrl + PROFILES + profileId;
    }

    /** One of that profile's views, addressed by the route's own sub-path. */
    public String viewUrl(String profileId, String viewPath) {
        return profileUrl(profileId) + "/" + viewPath;
    }

    private String requireId(HttpResponse<String> response, String field) throws IOException {
        if (!isSuccess(response)) {
            throw new IOException("Microscope answered " + response.statusCode() + ": " + response.body());
        }
        String id = MicroscopeJson.parseId(response.body(), field);
        if (id == null || id.isBlank()) {
            throw new IOException("Microscope answered without a " + field);
        }
        return id;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpRequest get(String url, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
    }

    private static boolean isSuccess(HttpResponse<String> response) {
        return response.statusCode() >= HTTP_OK_MIN && response.statusCode() <= HTTP_OK_MAX;
    }

    /** Zero when the file cannot be measured, which the by-path match reads as "name alone decides". */
    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Enough JSON string escaping for a filesystem path, which is the only thing this ever quotes. */
    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                quoted.append('\\').append(c);
            } else if (c < ' ') {
                quoted.append(String.format("\\u%04x", (int) c));
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
