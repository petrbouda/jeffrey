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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteSessionScan;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpToolMetrics;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Safe current-state diagnostics. No tool arguments, payloads or deployment secrets are retained. */
public final class McpDiagnostics {
    public static final String URI = "jeffrey://diagnostics";

    /** The application property bounding how long the remote is given to answer the reachability probe. */
    public static final String PROBE_TIMEOUT_PROPERTY = "jeffrey.microscope.mcp.diagnostics.probe-timeout";

    /** The probe budget when the property is not set, spelt as the ISO-8601 text the property takes. */
    public static final String DEFAULT_PROBE_TIMEOUT_TEXT = "PT2S";

    public static final Duration DEFAULT_PROBE_TIMEOUT = Duration.parse(DEFAULT_PROBE_TIMEOUT_TEXT);

    private final MicroscopeCoreRepositories repositories;
    private final RemoteManager remoteManager;
    private final ExternalMcpProperties properties;
    private final Clock clock;
    private final RemoteSessionScan scan;
    private final boolean servesSkills;

    /**
     * @param skills the skills the endpoint serves; the server block reports the extension when it
     *               holds at least one, as {@code server/discover} declares it
     */
    public McpDiagnostics(MicroscopeCoreRepositories repositories, RemoteManager remoteManager,
                          ExternalMcpProperties properties, Clock clock, Duration probeBudget,
                          McpSkillProvider skills) {
        this.servesSkills = skills.servesAny();
        this.repositories = repositories;
        this.remoteManager = remoteManager;
        this.properties = properties;
        this.clock = clock;
        this.scan = new RemoteSessionScan(remoteManager, probeBudget);
    }

    public String json(McpToolProvider tools, List<McpToolMetrics.Sample> metrics, long omittedMetricCalls) {
        ObjectNode result = Json.createObject().put("schemaVersion", 1).put("observedAt", clock.instant().toString());
        result.set("server", Json.readTree(new McpServerInfo(properties, tools, servesSkills).json()));
        List<ProfileInfo> profiles = repositories.findAllProfiles();
        long ready = profiles.stream().filter(ProfileInfo::enabled).count();
        result.putObject("profileReadiness").put("total", profiles.size()).put("ready", ready)
                .put("building", profiles.size() - ready)
                .put("scope", "Stored readiness; building rows may include interrupted work. Poll the recording or operation for lifecycle state.");
        ObjectNode remoteStatus = result.putObject(AdvertisedFamilies.REMOTE);
        boolean enabled = properties.remoteEnabled() && properties.advertises(AdvertisedFamilies.REMOTE);
        remoteStatus.put("enabled", enabled);
        if (enabled) {
            var configured = remoteManager.current();
            remoteStatus.put("configured", configured.isPresent());
            configured.ifPresent(remote -> {
                RemoteSessionScan.ProbeResult probe = scan.probe(remote);
                remoteStatus.put("reachable", probe.reachable())
                        .put("unreachable", failedAs(probe, RemoteSessionScan.Failure.Kind.UNREACHABLE))
                        .put("deadlineExceeded", failedAs(probe, RemoteSessionScan.Failure.Kind.DEADLINE_EXCEEDED));
            });
        }
        result.set("toolMetrics", Json.toTree(metrics));
        result.put("metricsCapped", omittedMetricCalls > 0).put("omittedMetricCalls", omittedMetricCalls);
        result.put("metricsScope", "This endpoint process; completed dispatched calls only, aggregated per advertised tool. Duration is nanoseconds; output size is UTF-8 bytes of the MCP result envelope. At most 256 tool names are retained.");
        // Bounded by construction - counts, and at most 256 tool metrics, with metricsCapped and
        // omittedMetricCalls declaring the cut - so it is written whole, like every structured answer.
        return Json.toString(result);
    }

    /** Whether the probe failed the given way, classified by the scan itself rather than by its wording. */
    private static boolean failedAs(RemoteSessionScan.ProbeResult probe, RemoteSessionScan.Failure.Kind kind) {
        return probe.failures().stream().anyMatch(failure -> failure.kind() == kind);
    }
}
