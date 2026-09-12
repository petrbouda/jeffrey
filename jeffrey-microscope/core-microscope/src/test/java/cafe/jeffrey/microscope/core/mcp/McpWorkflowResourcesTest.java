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

import cafe.jeffrey.profile.mcp.McpResource;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpWorkflowResourcesTest {
    @Test
    void evidenceUriRoutesDecodedIdentityThroughAdvertisedTool() {
        var resources = new McpResources(new ReflectiveToolset(new Evidence(), "profiles"));
        assertTrue(resources.templates().stream().anyMatch(r -> r.uri().equals("jeffrey://profile/{profileId}/evidence")));
        var contents = resources.read("jeffrey://profile/profile%201/evidence");
        assertEquals("profile 1", Json.readTree(contents.text()).path("profileId").asText());
        assertEquals(McpResource.APPLICATION_JSON, contents.mimeType());
        assertFalse(new McpResources(new ReflectiveToolset(new Evidence(), "other")).templates().stream()
                .anyMatch(r -> r.uri().endsWith("/evidence")));
    }

    @Test
    void diagnosticsAreLazyAndRecomputedOnRead() {
        AtomicInteger reads = new AtomicInteger();
        var resources = new McpResources(new ReflectiveToolset(new Evidence(), "profiles"),
                new ExternalMcpProperties(true, false, false, Set.of()),
                () -> "{\"reads\":" + reads.incrementAndGet() + "}");
        assertTrue(resources.resources().stream().anyMatch(r -> r.uri().equals("jeffrey://diagnostics")));
        assertEquals(0, reads.get());
        assertEquals(1, Json.readTree(resources.read("jeffrey://diagnostics").text()).path("reads").asInt());
        assertEquals(2, Json.readTree(resources.read("jeffrey://diagnostics").text()).path("reads").asInt());
    }

    public static class Evidence {
        @Tool(description = "Evidence snapshot")
        public String evidence(@ToolParam(required = true, description = "Profile") String profileId) {
            return Json.toString(Json.createObject().put("profileId", profileId));
        }
    }
}
