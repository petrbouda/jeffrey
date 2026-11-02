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

import cafe.jeffrey.microscope.connector.api.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorConnection;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpToolMetrics;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpDiagnosticsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
    private final MicroscopeCoreRepositories repositories = mock(MicroscopeCoreRepositories.class);
    private final ConnectorManager connectorManager = mock(ConnectorManager.class);
    private final ReflectiveToolset tools = new ReflectiveToolset(new Sample(), "profiles");

    @Test
    void reportsReadinessAndHonorsDisabledConnectorAccessWithoutLeakingConfiguration() {
        ProfileInfo ready = mock(ProfileInfo.class);
        ProfileInfo building = mock(ProfileInfo.class);
        when(ready.enabled()).thenReturn(true);
        when(repositories.findAllProfiles()).thenReturn(List.of(ready, building));
        when(connectorManager.current()).thenThrow(new AssertionError("Connector access is disabled"));
        var diagnostics = diagnostics(McpTestProperties.of(true, false, false, Set.of(), "jfr"));
        String text = diagnostics.json(tools, List.of(new McpToolMetrics.Sample("profiles_read", 2, 1, 40, 30, 60, 40)), 0);
        var result = Json.readTree(text);
        assertEquals(2, result.path("profileReadiness").path("total").asInt());
        assertEquals(1, result.path("profileReadiness").path("ready").asInt());
        assertEquals(1, result.path("profileReadiness").path("building").asInt());
        assertFalse(result.path("connector").path("enabled").asBoolean());
        assertEquals(2, result.path("toolMetrics").get(0).path("calls").asInt());
        assertEquals("jfr", result.path("server").path("preset").asText());
        assertFalse(result.has("environment"));
    }

    @Test
    void nothingConfiguredIsReportedAsSuchRatherThanAsUnreachable() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        when(connectorManager.current()).thenReturn(Optional.empty());

        var result = connectorStatus();

        assertTrue(result.path("enabled").asBoolean());
        assertFalse(result.path("configured").asBoolean(true));
        assertFalse(result.has("reachable"));
    }

    @Test
    void aReachableConnectorIsReportedAsReachable() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        ConnectorConnection connector = configured();
        when(connector.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("1"));

        var result = connectorStatus();

        assertTrue(result.path("configured").asBoolean());
        assertTrue(result.path("reachable").asBoolean());
        assertFalse(result.path("unreachable").asBoolean(true));
        assertFalse(result.path("deadlineExceeded").asBoolean(true));
    }

    @Test
    void separatesAnUnreachableConnectorWithoutReturningItsAddressOrErrorDetails() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        ConnectorConnection connector = configured();
        when(connector.infoOrThrow()).thenThrow(
                new JeffreyException(ErrorType.INTERNAL, ErrorCode.CONNECTOR_UNAVAILABLE, "credential-secret"));

        String text = diagnostics(McpTestProperties.of(true, true, false, Set.of())).json(tools, List.of(), 0);
        var result = Json.readTree(text).path("connector");

        assertFalse(result.path("reachable").asBoolean(true));
        assertTrue(result.path("unreachable").asBoolean());
        assertFalse(result.path("deadlineExceeded").asBoolean(true));
        assertFalse(text.contains("credential-secret"));
        assertFalse(text.contains("internal.file.core.windows.net"));
    }

    @Test
    void separatesAMissedDeadline() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        ConnectorConnection connector = configured();
        // A probe that never answers, against a budget far shorter than the default
        when(connector.infoOrThrow()).thenAnswer(invocation -> {
            new CountDownLatch(1).await();
            return null;
        });

        String text = diagnostics(McpTestProperties.of(true, true, false, Set.of()), Duration.ofMillis(50))
                .json(tools, List.of(), 0);
        var result = Json.readTree(text).path("connector");

        assertFalse(result.path("unreachable").asBoolean(true));
        assertTrue(result.path("deadlineExceeded").asBoolean());
    }

    /**
     * The flags come from the kind the scan assigned, not from the wording of its reason. A connector
     * whose unavailability is explained with words that happen to spell out a deadline is still
     * unreachable, and a failure that merely says "unreachable" in its message is neither.
     */
    @Test
    void classifiesByTheKindTheScanAssignedRatherThanByTheWording() {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        ConnectorConnection unavailable = configured();
        when(unavailable.infoOrThrow()).thenThrow(new JeffreyException(
                ErrorType.INTERNAL, ErrorCode.CONNECTOR_UNAVAILABLE, "deadline exceeded while dialling"));

        var first = connectorStatus();

        assertTrue(first.path("unreachable").asBoolean());
        assertFalse(first.path("deadlineExceeded").asBoolean(true));

        ConnectorConnection broken = configured();
        when(broken.infoOrThrow()).thenThrow(new IllegalStateException("unreachable"));

        var second = connectorStatus();

        assertFalse(second.path("reachable").asBoolean(true));
        assertFalse(second.path("unreachable").asBoolean(true));
        assertFalse(second.path("deadlineExceeded").asBoolean(true));
    }

    private McpDiagnostics diagnostics(ExternalMcpProperties properties) {
        return diagnostics(properties, Duration.ofSeconds(1));
    }

    private McpDiagnostics diagnostics(ExternalMcpProperties properties, Duration probeTimeout) {
        return new McpDiagnostics(repositories, connectorManager, properties,
                CLOCK, probeTimeout, McpSkillProvider.NONE);
    }

    private JsonNode connectorStatus() {
        String text = diagnostics(McpTestProperties.of(true, true, false, Set.of())).json(tools, List.of(), 0);
        return Json.readTree(text).path("connector");
    }

    private ConnectorConnection configured() {
        ConnectorConnection connection = mock(ConnectorConnection.class);
        when(connection.info()).thenReturn(new ConnectorInfo("internal", new AzureFilesAddress(
                "https://internal.file.core.windows.net", "recordings", "",
                AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false)));
        when(connectorManager.current()).thenReturn(Optional.of(connection));
        return connection;
    }

    static class Sample {
        @Tool(description = "Read")
        public String read() {
            return "ok";
        }
    }
}
