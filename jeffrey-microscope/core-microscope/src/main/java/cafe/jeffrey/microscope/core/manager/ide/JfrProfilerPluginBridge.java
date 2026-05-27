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

package cafe.jeffrey.microscope.core.manager.ide;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * {@link IdeBridge} for {@link IdeMode#JFR_PROFILER_PLUGIN}: forwards requests to the JFR Profiler
 * IntelliJ plugin's {@code /ide/{fqn}.{method}} HTTP contract (default {@code http://localhost:4243}).
 * The browser talks only to the same-origin Microscope backend; this class performs the cross-port
 * hop server-side, which avoids the CORS / mixed-content problems of calling the plugin directly
 * from the SPA.
 *
 * <p>Reaching the IDE is best-effort: when the plugin is disabled, unconfigured, unreachable, or
 * rejects the request, the operations return a failed result with a human-readable message rather
 * than throwing — the IDE being offline is an expected condition.
 */
public class JfrProfilerPluginBridge implements IdeBridge {

    private static final Logger LOG = LoggerFactory.getLogger(JfrProfilerPluginBridge.class);

    private static final String IDE_PATH_PREFIX = "/ide/";
    private static final String HAS_PATH_PREFIX = "/ide/has/";
    private static final String TRAILING_SLASH = "/";
    private static final String SEGMENT_SEPARATOR = ".";
    private static final String SEGMENT_SPLIT_REGEX = "\\.";
    private static final char METHOD_PREFIX_SEPARATOR = '.';
    private static final String ENCODER_PLUS = "+";
    private static final String ENCODED_SPACE = "%20";

    private static final String MSG_NO_BASE_URL = "IDE base URL is not configured";
    private static final String MSG_IDE_UNREACHABLE = "Could not reach the IDE plugin — is it running?";
    private static final String MSG_IDE_REJECTED = "The IDE plugin rejected the request";
    private static final String MSG_SOURCE_UNAVAILABLE = "Source is not available for this class";

    private final String baseUrl;
    private final RestClient restClient;

    public JfrProfilerPluginBridge(String baseUrl, RestClient.Builder restClientBuilder) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public IdeMode mode() {
        return IdeMode.JFR_PROFILER_PLUGIN;
    }

    /**
     * Class-level presence check via the JFR Profiler plugin's {@code GET /ide/has/<fqn>} endpoint
     * (returns {@code {"resolved": bool}}). The fqn is sent as a single URL-encoded path segment, the
     * way the plugin decodes it. Best-effort: missing base URL/fqn, non-2xx, or any error → {@code false}.
     */
    @Override
    public boolean hasClass(String profileId, String fqn) {
        if (baseUrl == null || fqn == null || fqn.isBlank()) {
            return false;
        }

        String url = baseUrl + HAS_PATH_PREFIX + encodeSegment(fqn);
        try {
            ResponseEntity<HasResponse> response = restClient.get()
                    .uri(URI.create(url))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        // Suppress the default 4xx/5xx exception; the status is inspected below.
                    })
                    .toEntity(HasResponse.class);

            HttpStatusCode status = response.getStatusCode();
            HasResponse body = response.getBody();
            return status.is2xxSuccessful() && body != null && body.resolved();
        } catch (Exception e) {
            LOG.debug("Failed to reach IDE plugin for has-class: url={} reason={}", url, e.getMessage());
            return false;
        }
    }

    @Override
    public IdeOpenResult open(IdeOpenRequest request) {
        if (baseUrl == null) {
            LOG.warn("IDE open requested but base URL is not configured");
            return IdeOpenResult.failed(MSG_NO_BASE_URL, IdeFailureReason.DISABLED);
        }

        String url = baseUrl + IDE_PATH_PREFIX + buildEncodedPath(request.fqn(), request.method());
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new IdeOpenBody(request.method(), request.line()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        // Suppress the default 4xx/5xx exception; the status is inspected below.
                    })
                    .toBodilessEntity();

            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                LOG.warn("IDE open dispatched: url={} status={}", url, status.value());
                return IdeOpenResult.succeeded();
            }
            LOG.debug("IDE plugin returned non-success status: url={} status={}", url, status.value());
            return IdeOpenResult.failed(MSG_IDE_REJECTED, IdeFailureReason.NOT_RESOLVED);
        } catch (Exception e) {
            LOG.error("Failed to reach IDE plugin: url={} reason={}", url, e.getMessage());
            return IdeOpenResult.failed(MSG_IDE_UNREACHABLE, IdeFailureReason.UNREACHABLE);
        }
    }

    /**
     * Fetches the raw source text of a class from the IDE plugin's {@code GET /ide/{fqn}.{method}}
     * endpoint. Like {@link #open(IdeOpenRequest)} this is best-effort: an offline plugin or a class
     * the plugin cannot resolve yields a failed {@link IdeSourceResult} rather than an exception.
     */
    @Override
    public IdeSourceResult fetchSource(IdeSourceRequest request) {
        if (baseUrl == null) {
            LOG.warn("IDE source requested but base URL is not configured");
            return IdeSourceResult.failed(MSG_NO_BASE_URL);
        }

        String url = baseUrl + IDE_PATH_PREFIX + buildEncodedPath(request.fqn(), request.method());
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(URI.create(url))
                    .accept(MediaType.TEXT_PLAIN, MediaType.ALL)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        // Suppress the default 4xx/5xx exception; the status is inspected below.
                    })
                    .toEntity(String.class);

            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                LOG.warn("IDE plugin returned non-success status for source: url={} status={}", url, status.value());
                return IdeSourceResult.failed(MSG_SOURCE_UNAVAILABLE);
            }

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                LOG.warn("IDE plugin returned empty source: url={}", url);
                return IdeSourceResult.failed(MSG_SOURCE_UNAVAILABLE);
            }

            LOG.debug("IDE source fetched: url={} length={}", url, body.length());
            return IdeSourceResult.succeeded(body, false);
        } catch (Exception e) {
            LOG.error("Failed to reach IDE plugin for source: url={} reason={}", url, e.getMessage());
            return IdeSourceResult.failed(MSG_IDE_UNREACHABLE);
        }
    }

    /**
     * The JFR Profiler plugin is a single-URL connection: there is no window to pick or disconnect
     * ({@code selectable=false}), and it counts as always linked once this mode is configured. The
     * frontend renders this as an auto-linked panel ("Connected using JFR Profiler Plugin").
     */
    @Override
    public IdeTargetStatus targetStatus(String profileId) {
        return new IdeTargetStatus(false, true, null, null, 0, 0);
    }

    /**
     * Reproduces the path the SPA used to build: the class prefix is stripped from {@code method},
     * the resulting {@code fqn.simpleMethod} is split on dots, each segment is URL-encoded, and the
     * segments are re-joined with dots. {@link URLEncoder} encodes spaces as {@code +}, so they are
     * rewritten to {@code %20} for parity with the browser's {@code encodeURIComponent}.
     */
    private static String buildEncodedPath(String fqn, String method) {
        int separatorIndex = method.indexOf(METHOD_PREFIX_SEPARATOR);
        String simpleMethod = separatorIndex >= 0 ? method.substring(separatorIndex + 1) : method;
        String dotted = fqn + SEGMENT_SEPARATOR + simpleMethod;

        String[] segments = dotted.split(SEGMENT_SPLIT_REGEX, -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append(SEGMENT_SEPARATOR);
            }
            builder.append(encodeSegment(segments[i]));
        }
        return builder.toString();
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace(ENCODER_PLUS, ENCODED_SPACE);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.strip();
        return trimmed.endsWith(TRAILING_SLASH) ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private record IdeOpenBody(String method, int line) {
    }

    private record HasResponse(boolean resolved) {
    }
}
