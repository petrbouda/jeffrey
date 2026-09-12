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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionScan;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.mcp.McpToolMetrics;
import cafe.jeffrey.profile.mcp.McpToolProvider;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Safe current-state diagnostics. No tool arguments, payloads or deployment secrets are retained. */
public final class McpDiagnostics {
    public static final String URI = "jeffrey://diagnostics";
    private final MicroscopeCoreRepositories repositories;
    private final HubsManager hubs;
    private final ExternalMcpProperties properties;
    private final Clock clock;
    private final HubSessionScan scan;
    public McpDiagnostics(MicroscopeCoreRepositories repositories, HubsManager hubs,
                          ExternalMcpProperties properties, Clock clock, Duration probeBudget) {
        this.repositories = repositories;
        this.hubs = hubs;
        this.properties = properties;
        this.clock = clock;
        this.scan = new HubSessionScan(hubs, probeBudget);
    }

    public String json(McpToolProvider tools, List<McpToolMetrics.Sample> metrics) {
        return json(tools, metrics, 0);
    }

    public String json(McpToolProvider tools, List<McpToolMetrics.Sample> metrics, long omittedMetricCalls) {
        ObjectNode result = Json.createObject().put("schemaVersion", 1).put("observedAt", clock.instant().toString());
        result.set("server", Json.readTree(new McpServerInfo(properties, tools).json()));
        List<ProfileInfo> profiles = repositories.findAllProfiles();
        long ready = profiles.stream().filter(ProfileInfo::enabled).count();
        result.putObject("profileReadiness").put("total", profiles.size()).put("ready", ready)
                .put("building", profiles.size() - ready)
                .put("scope", "Stored readiness; building rows may include interrupted work. Poll the recording or operation for lifecycle state.");
        ObjectNode hubStatus = result.putObject("hubs");
        boolean enabled = properties.hubsEnabled() && properties.advertises("hubs");
        hubStatus.put("enabled", enabled);
        if (enabled) {
            var managers = hubs.findAll();
            HubSessionScan.ProbeResult probe = scan.probeDetails(managers);
            long deadlines = probe.failures().stream().filter(f -> f.reason().startsWith("deadline exceeded")).count();
            long unreachable = probe.failures().stream().filter(f -> f.reason().equals("unreachable")).count();
            hubStatus.put("total", managers.size()).put("reachable", probe.versions().size())
                    .put("unreachable", unreachable).put("deadlineExceeded", deadlines)
                    .put("otherFailures", probe.failures().size() - deadlines - unreachable)
                    .put("complete", probe.failures().isEmpty());
        }
        result.set("toolMetrics", Json.toTree(metrics));
        result.put("metricsCapped", omittedMetricCalls > 0).put("omittedMetricCalls", omittedMetricCalls);
        result.put("metricsScope", "This endpoint process; completed dispatched calls only, aggregated per advertised tool. Duration is nanoseconds; output size is UTF-8 bytes of the MCP result envelope. At most 256 tool names are retained.");
        return McpToolOutput.json(result);
    }
}
