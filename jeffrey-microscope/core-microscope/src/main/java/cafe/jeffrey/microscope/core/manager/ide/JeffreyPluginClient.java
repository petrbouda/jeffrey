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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Thin HTTP client for a single Jeffrey IntelliJ plugin instance, addressed by port on localhost
 * ({@code http://127.0.0.1:<port>/api/jeffrey/}). Every call is best-effort: an unreachable port or
 * an error response yields an empty/failed result rather than an exception, so callers can probe the
 * whole port range cheaply.
 *
 * <p>Two clients, not one, because the two things this class does have nothing in common but their
 * host. Discovery walks twenty-one closed ports and must give up on each in milliseconds; an
 * operation asks IntelliJ to search its indexes for a class or to hand over a whole file, which on a
 * cold index takes longer than any scan should ever wait. Sharing one two-hundred-millisecond read
 * timeout between them meant a slow but perfectly healthy resolve came back as {@code null} and was
 * reported to the reader as "the IDE window is no longer open".
 *
 * <p>Both are built from {@link RestClient.Builder}s supplied by the configuration. Injecting the
 * builders lets tests bind a Spring {@code MockRestServiceServer} to either one.
 */
public final class JeffreyPluginClient {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyPluginClient.class);

    private static final String HOST = "127.0.0.1";

    /**
     * The answers that mean "this server does not have that endpoint" rather than "that endpoint
     * failed": the route is missing, or it exists for another method.
     */
    private static final Set<Integer> ENDPOINT_ABSENT_STATUSES = Set.of(404, 405);
    private static final String BASE = "http://" + HOST + ":{port}/api/jeffrey/";

    /** Walks the port range: everything here must fail fast on a closed port. */
    private final RestClient discoveryClient;

    /** Asks a window that answered to do real work, which is allowed to take its time. */
    private final RestClient operationsClient;

    public JeffreyPluginClient(RestClient.Builder discoveryBuilder, RestClient.Builder operationsBuilder) {
        this.discoveryClient = discoveryBuilder.build();
        this.operationsClient = operationsBuilder.build();
    }

    public Optional<PluginInstance> instance(int port) {
        try {
            PluginInstance instance = discoveryClient.get()
                    .uri(BASE + "instance", port)
                    .retrieve()
                    .body(PluginInstance.class);
            return Optional.ofNullable(instance);
        } catch (Exception e) {
            // Spams because it's scan used ports
            // LOG.warn("Cannot get information about IDE instance: port={} reason={}", port, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Existence check against an IDE window: {@code className} is required, {@code methodName}
     * optional. With a method the plugin resolves by class + method; without one, by class alone.
     */
    public boolean has(int port, String projectId, String className, String methodName) {
        try {
            PluginHas result = (methodName == null || methodName.isBlank())
                    ? discoveryClient.get()
                            .uri(BASE + "has?class={class}&projectId={projectId}", port, className, projectId)
                            .retrieve()
                            .body(PluginHas.class)
                    : discoveryClient.get()
                            .uri(BASE + "has?class={class}&method={method}&projectId={projectId}",
                                    port, className, methodName, projectId)
                            .retrieve()
                            .body(PluginHas.class);
            return result != null && result.found();
        } catch (Exception e) {
            LOG.warn("Cannot get information HAS : port={} reason={}", port, e.getMessage());
            return false;
        }
    }

    public PluginNavigateResult navigate(int port, NavigateBody body) {
        try {
            return operationsClient.post()
                    .uri(BASE + "navigate", port)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PluginNavigateResult.class);
        } catch (Exception e) {
            LOG.warn("Failed to navigate via IDE plugin: port={} reason={}", port, e.getMessage());
            return null;
        }
    }

    /**
     * Locates a source position without opening it — the plugin's {@code resolve} endpoint, added in
     * protocol version 2. Answers in the same shape as {@link #navigate}, so the two differ only in
     * whether the developer's editor moves.
     *
     * <p>A plugin too old to know the endpoint answers rather than staying silent, and that is worth
     * telling apart from an IDE that has gone away: {@link Unsupported} is thrown for the first so the
     * caller can say "update the plugin" instead of "the IDE is not running".
     *
     * <p>Only a 404 or a 405 means that, though. The plugin also answers 404 when its integration is
     * switched off, and 500 when a lookup threw inside the IDE — reading either as "too old" told a
     * developer to update a plugin that was current, and hid the switch they had flipped. Those are
     * reported as an unavailable window, the same as an unreachable port; the 404 case is
     * indistinguishable from an old plugin on the wire, and re-discovery is what tells them apart.
     */
    public PluginNavigateResult resolve(int port, NavigateBody body) {
        try {
            return operationsClient.post()
                    .uri(BASE + "resolve", port)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PluginNavigateResult.class);
        } catch (RestClientResponseException e) {
            if (ENDPOINT_ABSENT_STATUSES.contains(e.getStatusCode().value())) {
                LOG.debug("IDE plugin does not serve resolve: port={} status={}", port, e.getStatusCode());
                throw new Unsupported();
            }
            LOG.warn("IDE plugin refused a resolve: port={} status={}", port, e.getStatusCode());
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to resolve a location via IDE plugin: port={} reason={}", port, e.getMessage());
            return null;
        }
    }

    public PluginSourceResult source(int port, String projectId, String className) {
        try {
            return operationsClient.get()
                    .uri(BASE + "source?projectId={projectId}&className={className}", port, projectId, className)
                    .retrieve()
                    .body(PluginSourceResult.class);
        } catch (Exception e) {
            LOG.warn("Failed to fetch source via IDE plugin: port={} reason={}", port, e.getMessage());
            return null;
        }
    }

    public record NavigateBody(
            String projectId,
            String className,
            String methodName,
            int lineNumber,
            String recordingTime) {
    }

    public record PluginInstance(
            int protocolVersion,
            String instanceId,
            String ideName,
            String ideEdition,
            String ideVersion,
            long pid,
            int port,
            String startedAt,
            List<PluginProject> projects) {
    }

    public record PluginProject(
            String id,
            String name,
            String basePath,
            boolean trusted,
            boolean focused,
            String vcsBranch,
            String headCommit) {
    }

    /**
     * The IDE is there but does not serve the endpoint that was called — an older plugin. Unchecked
     * and empty because the only fact it carries is its own type.
     */
    public static final class Unsupported extends RuntimeException {

        public Unsupported() {
            super("The Jeffrey IntelliJ plugin is too old to serve this request");
        }
    }

    public record PluginNavigateResult(
            boolean resolved,
            String source,
            String file,
            Integer line,
            boolean decompiled,
            boolean imprecise,
            boolean stale,
            String sourceMTime,
            String reason) {
    }

    public record PluginSourceResult(
            boolean resolved,
            String content,
            String file,
            boolean decompiled,
            String reason) {
    }

    public record PluginHas(boolean found, String projectId) {
    }
}
