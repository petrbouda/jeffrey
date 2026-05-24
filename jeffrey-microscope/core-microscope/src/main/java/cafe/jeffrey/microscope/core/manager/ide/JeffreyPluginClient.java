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

import java.util.Optional;

/**
 * Thin HTTP client for a single Jeffrey IntelliJ plugin instance, addressed by port on localhost
 * ({@code http://127.0.0.1:<port>/api/jeffrey/}). Every call is best-effort: an unreachable port or
 * an error response yields an empty/failed result rather than an exception, so callers can probe the
 * whole port range cheaply.
 *
 * <p>The {@link RestClient} is built from a {@link RestClient.Builder} supplied by the configuration
 * (which sets the scan-friendly timeouts). Injecting the builder lets tests bind a Spring
 * {@code MockRestServiceServer} to it.
 */
public final class JeffreyPluginClient {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyPluginClient.class);

    private static final String HOST = "127.0.0.1";
    private static final String BASE = "http://" + HOST + ":{port}/api/jeffrey/";

    private final RestClient restClient;

    public JeffreyPluginClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public Optional<PluginInstance> instance(int port) {
        try {
            PluginInstance instance = restClient.get()
                    .uri(BASE + "instance", port)
                    .retrieve()
                    .body(PluginInstance.class);
            return Optional.ofNullable(instance);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean hasClass(int port, String projectId, String fqcn) {
        try {
            PluginHasClass result = restClient.get()
                    .uri(BASE + "has-class?fqcn={fqcn}&projectId={projectId}", port, fqcn, projectId)
                    .retrieve()
                    .body(PluginHasClass.class);
            return result != null && result.found();
        } catch (Exception e) {
            return false;
        }
    }

    public PluginNavigateResult navigate(int port, NavigateBody body) {
        try {
            return restClient.post()
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

    public PluginSourceResult source(int port, String projectId, String className) {
        try {
            return restClient.get()
                    .uri(BASE + "source?projectId={projectId}&className={className}", port, projectId, className)
                    .retrieve()
                    .body(PluginSourceResult.class);
        } catch (Exception e) {
            LOG.warn("Failed to fetch source via IDE plugin: port={} reason={}", port, e.getMessage());
            return null;
        }
    }

    public record NavigateBody(String projectId, String className, String methodName, int lineNumber, String recordingTime) {
    }

    public record PluginInstance(
            int protocolVersion, String instanceId, String ideName, String ideEdition, String ideVersion,
            long pid, int port, String startedAt, java.util.List<PluginProject> projects) {
    }

    public record PluginProject(String id, String name, String basePath, boolean trusted, boolean focused, String vcsBranch) {
    }

    public record PluginNavigateResult(
            boolean resolved, String source, String file, Integer line, boolean decompiled,
            boolean imprecise, boolean stale, String sourceMTime, String reason) {
    }

    public record PluginSourceResult(boolean resolved, String content, String file, boolean decompiled, String reason) {
    }

    public record PluginHasClass(boolean found, String projectId) {
    }
}
