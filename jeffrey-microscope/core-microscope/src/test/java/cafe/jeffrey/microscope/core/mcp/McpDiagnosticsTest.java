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

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpToolMetrics;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpDiagnosticsTest {
    private final MicroscopeCoreRepositories repositories = mock(MicroscopeCoreRepositories.class);
    private final HubsManager hubs = mock(HubsManager.class);
    private final ReflectiveToolset tools = new ReflectiveToolset(new Sample(), "profiles");

    @Test
    void reportsReadinessAndHonorsDisabledHubAccessWithoutLeakingConfiguration() {
        ProfileInfo ready = mock(ProfileInfo.class);
        ProfileInfo building = mock(ProfileInfo.class);
        when(ready.enabled()).thenReturn(true);
        when(repositories.findAllProfiles()).thenReturn(List.of(ready, building));
        when(hubs.findAll()).thenThrow(new AssertionError("Hub access is disabled"));
        var diagnostics = new McpDiagnostics(repositories, hubs,
                new ExternalMcpProperties(true, false, false, Set.of(), "jfr"), Clock.systemUTC(), Duration.ofSeconds(1));
        String text = diagnostics.json(tools, List.of(new McpToolMetrics.Sample("profiles_read", 2, 1, 40, 30, 60, 40)));
        var result = Json.readTree(text);
        assertEquals(2, result.path("profileReadiness").path("total").asInt());
        assertEquals(1, result.path("profileReadiness").path("ready").asInt());
        assertEquals(1, result.path("profileReadiness").path("building").asInt());
        assertFalse(result.path("hubs").path("enabled").asBoolean());
        assertEquals(2, result.path("toolMetrics").get(0).path("calls").asInt());
        assertEquals("jfr", result.path("server").path("preset").asText());
        assertFalse(result.has("environment"));
    }

    @Test
    void separatesDeadlineFailuresAndUnreachableHubsWithoutReturningAddressesOrErrorDetails() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        HubManager ready = hub("ready");
        HubManager unavailable = hub("unavailable");
        HubManager timeout = hub("timeout");
        when(ready.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1", 1));
        when(unavailable.infoOrThrow()).thenThrow(Status.UNAVAILABLE.withDescription("credential-secret").asRuntimeException());
        when(timeout.infoOrThrow()).thenThrow(Status.DEADLINE_EXCEEDED.withDescription("credential-secret").asRuntimeException());
        when(hubs.findAll()).thenReturn(List.of(ready, unavailable, timeout));
        var diagnostics = new McpDiagnostics(repositories, hubs,
                new ExternalMcpProperties(true, true, false, Set.of()), Clock.systemUTC(), Duration.ofSeconds(1));
        String text = diagnostics.json(tools, List.of());
        var result = Json.readTree(text).path("hubs");
        assertEquals(1, result.path("reachable").asInt());
        assertEquals(1, result.path("unreachable").asInt());
        assertEquals(1, result.path("deadlineExceeded").asInt());
        assertFalse(text.contains("credential-secret"));
        assertFalse(text.contains("internal.example"));
    }

    private static HubManager hub(String id) {
        HubManager manager = mock(HubManager.class);
        when(manager.info()).thenReturn(new HubInfo(id, id,
                new HubAddress("internal.example", 9090, true), Instant.EPOCH, HubSource.CONFIG));
        return manager;
    }

    static class Sample {
        @Tool(description = "Read")
        public String read() {
            return "ok";
        }
    }
}
