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

package cafe.jeffrey.hub.core.mcp;

import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.StreamingWindow;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class HubMcpControllerTest {
    @Test
    void directHubEndpointListsAndRunsTheAggregateTools(@TempDir Path temp) throws Exception {
        try (var service = new HubActivityService(req -> new ReplayStreamSubscription(req.sessionId(), List.of(), req.eventTypes(),
                new StreamingWindow(Instant.ofEpochMilli(req.startTime()), Instant.ofEpochMilli(req.endTime())),
                temp, req.workspaceId(), req.projectId()))) {
            var mvc = MockMvcBuilders.standaloneSetup(new HubMcpController(service, "localhost")).build();
            String listed = mvc.perform(post("/api/mcp").contentType(APPLICATION_JSON)
                    .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var tools = Json.readTree(listed).path("result").path("tools");
            assertEquals(3, tools.size());
            assertTrue(listed.contains("hub_eventActivity"));
            assertTrue(listed.contains("hub_activityStatus"));
            assertTrue(listed.contains("hub_activityCancel"));
            String body = """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"hub_eventActivity",
                    "arguments":{"workspaceId":"w","projectId":"p","sessionId":"s","startTime":0,"endTime":86400000}}}
                    """;
            String response = mvc.perform(post("/api/mcp").header("MCP-Protocol-Version", "2025-06-18")
                    .contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var result = Json.readTree(response).path("result").path("structuredContent");
            assertFalse(result.path("scanId").asText().isBlank(), response);
            assertEquals(288, result.path("totalBuckets").asInt());
            assertEquals(268, result.path("omittedBuckets").asInt());
            assertEquals("w", result.path("workspaceId").asText());
            mvc.perform(post("/api/mcp").contentType(APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/mcp").header("Origin", "https://foreign.example").contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/mcp").with(req -> { req.setServerName("foreign.example"); return req; })
                    .contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
    }

    @Test
    void enabledConfigurationWiresTheControllerAndService(@TempDir Path temp) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test", Map.of("jeffrey.hub.mcp.enabled", "true")));
            context.registerBean(cafe.jeffrey.hub.persistence.api.HubPlatformRepositories.class,
                    () -> org.mockito.Mockito.mock(cafe.jeffrey.hub.persistence.api.HubPlatformRepositories.class));
            context.registerBean(cafe.jeffrey.hub.core.project.repository.RepositoryStorage.Factory.class,
                    () -> org.mockito.Mockito.mock(cafe.jeffrey.hub.core.project.repository.RepositoryStorage.Factory.class));
            context.registerBean(cafe.jeffrey.hub.core.HubJeffreyDirs.class, () -> new cafe.jeffrey.hub.core.HubJeffreyDirs(temp));
            context.register(HubMcpConfiguration.class, HubMcpController.class);
            context.refresh();
            assertNotNull(context.getBean(HubMcpController.class));
            assertNotNull(context.getBean(HubActivityService.class));
        }
    }

    @Test
    void featureIsAbsentUnlessExplicitlyEnabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(HubMcpConfiguration.class, HubMcpController.class);
            context.refresh();
            assertTrue(context.getBeansOfType(HubActivityService.class).isEmpty());
            assertTrue(context.getBeansOfType(HubMcpController.class).isEmpty());
        }
    }
}
