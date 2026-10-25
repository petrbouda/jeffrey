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

import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpToolMetrics;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import io.grpc.Status;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpDiagnosticsTest {
    private final MicroscopeCoreRepositories repositories = mock(MicroscopeCoreRepositories.class);
    private final RemoteManager remoteManager = mock(RemoteManager.class);
    private final ReflectiveToolset tools = new ReflectiveToolset(new Sample(), "profiles");

    @Test
    void reportsReadinessAndHonorsDisabledRemoteAccessWithoutLeakingConfiguration() {
        ProfileInfo ready = mock(ProfileInfo.class);
        ProfileInfo building = mock(ProfileInfo.class);
        when(ready.enabled()).thenReturn(true);
        when(repositories.findAllProfiles()).thenReturn(List.of(ready, building));
        when(remoteManager.current()).thenThrow(new AssertionError("Remote access is disabled"));
        var diagnostics = diagnostics(McpTestProperties.of(true, false, false, Set.of(), "jfr"));
        String text = diagnostics.json(tools, List.of(new McpToolMetrics.Sample("profiles_read", 2, 1, 40, 30, 60, 40)), 0);
        var result = Json.readTree(text);
        assertEquals(2, result.path("profileReadiness").path("total").asInt());
        assertEquals(1, result.path("profileReadiness").path("ready").asInt());
        assertEquals(1, result.path("profileReadiness").path("building").asInt());
        assertFalse(result.path("remote").path("enabled").asBoolean());
        assertEquals(2, result.path("toolMetrics").get(0).path("calls").asInt());
        assertEquals("jfr", result.path("server").path("preset").asText());
        assertFalse(result.has("environment"));
    }

    @Test
    void nothingConnectedIsReportedAsSuchRatherThanAsUnreachable() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        when(remoteManager.current()).thenReturn(Optional.empty());

        var result = remoteStatus();

        assertTrue(result.path("enabled").asBoolean());
        assertFalse(result.path("connected").asBoolean(true));
        assertFalse(result.has("reachable"));
    }

    @Test
    void aReachableRemoteIsReportedAsReachable() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        RemoteConnection remote = connected();
        when(remote.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1"));

        var result = remoteStatus();

        assertTrue(result.path("connected").asBoolean());
        assertTrue(result.path("reachable").asBoolean());
        assertFalse(result.path("unreachable").asBoolean(true));
        assertFalse(result.path("deadlineExceeded").asBoolean(true));
    }

    @Test
    void separatesAnUnreachableRemoteWithoutReturningItsAddressOrErrorDetails() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        RemoteConnection remote = connected();
        when(remote.infoOrThrow()).thenThrow(Status.UNAVAILABLE.withDescription("credential-secret").asRuntimeException());

        String text = diagnostics(McpTestProperties.of(true, true, false, Set.of())).json(tools, List.of(), 0);
        var result = Json.readTree(text).path("remote");

        assertFalse(result.path("reachable").asBoolean(true));
        assertTrue(result.path("unreachable").asBoolean());
        assertFalse(result.path("deadlineExceeded").asBoolean(true));
        assertFalse(text.contains("credential-secret"));
        assertFalse(text.contains("internal.example"));
    }

    @Test
    void separatesAMissedDeadline() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        RemoteConnection remote = connected();
        when(remote.infoOrThrow()).thenThrow(Status.DEADLINE_EXCEEDED.withDescription("credential-secret").asRuntimeException());

        var result = remoteStatus();

        assertFalse(result.path("unreachable").asBoolean(true));
        assertTrue(result.path("deadlineExceeded").asBoolean());
    }

    /**
     * The flags come from the kind the scan assigned, not from the wording of its reason. A remote
     * whose unavailability is explained with words that happen to spell out a deadline is still
     * unreachable, and a failure that merely says "unreachable" in its message is neither.
     */
    @Test
    void classifiesByTheKindTheScanAssignedRatherThanByTheWording() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        RemoteConnection unavailable = connected();
        when(unavailable.infoOrThrow()).thenThrow(new JeffreyException(
                ErrorType.INTERNAL, ErrorCode.REMOTE_UNAVAILABLE, "deadline exceeded while dialling"));

        var first = remoteStatus();

        assertTrue(first.path("unreachable").asBoolean());
        assertFalse(first.path("deadlineExceeded").asBoolean(true));

        RemoteConnection broken = connected();
        when(broken.infoOrThrow()).thenThrow(new IllegalStateException("unreachable"));

        var second = remoteStatus();

        assertFalse(second.path("reachable").asBoolean(true));
        assertFalse(second.path("unreachable").asBoolean(true));
        assertFalse(second.path("deadlineExceeded").asBoolean(true));
    }

    private McpDiagnostics diagnostics(ExternalMcpProperties properties) {
        return new McpDiagnostics(repositories, remoteManager, properties,
                Clock.systemUTC(), Duration.ofSeconds(1), McpSkillProvider.NONE);
    }

    private JsonNode remoteStatus() {
        String text = diagnostics(McpTestProperties.of(true, true, false, Set.of())).json(tools, List.of(), 0);
        return Json.readTree(text).path("remote");
    }

    private RemoteConnection connected() {
        RemoteConnection connection = mock(RemoteConnection.class);
        when(connection.info()).thenReturn(new RemoteInfo("internal",
                new HubAddress("internal.example", 9090, true), Instant.EPOCH, RemoteSource.CONFIG));
        when(remoteManager.current()).thenReturn(Optional.of(connection));
        return connection;
    }

    static class Sample {
        @Tool(description = "Read")
        public String read() {
            return "ok";
        }
    }
}
