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

import cafe.jeffrey.microscope.mcp.protocol.McpResource;
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
        var resources = new McpResources(new ReflectiveToolset(new Evidence(), "profiles"),
                McpWorkflowResourcesTest::noDocuments, McpTestProperties.of(true, true, true, Set.of()), null, false);
        assertTrue(resources.templates().stream().anyMatch(r -> r.uri().equals("jeffrey://profile/{profileId}/evidence")));
        var contents = resources.read("jeffrey://profile/profile%201/evidence");
        assertEquals("profile 1", Json.readTree(contents.text()).path("profileId").asText());
        assertEquals(McpResource.APPLICATION_JSON, contents.mimeType());
        assertFalse(new McpResources(new ReflectiveToolset(new Evidence(), "other"),
                McpWorkflowResourcesTest::noDocuments, McpTestProperties.of(true, true, true, Set.of()), null, false)
                .templates().stream()
                .anyMatch(r -> r.uri().endsWith("/evidence")));
    }

    @Test
    void diagnosticsAreLazyAndRecomputedOnRead() {
        AtomicInteger reads = new AtomicInteger();
        var resources = new McpResources(new ReflectiveToolset(new Evidence(), "profiles"),
                McpWorkflowResourcesTest::noDocuments, McpTestProperties.of(true, false, false, Set.of()),
                () -> "{\"reads\":" + reads.incrementAndGet() + "}", false);
        assertTrue(resources.resources().stream().anyMatch(r -> r.uri().equals("jeffrey://diagnostics")));
        assertEquals(0, reads.get());
        assertEquals(1, Json.readTree(resources.read("jeffrey://diagnostics").text()).path("reads").asInt());
        assertEquals(2, Json.readTree(resources.read("jeffrey://diagnostics").text()).path("reads").asInt());
    }

    /** Neither test reads a profile document, so none is there to read. */
    private static McpProfileDocuments noDocuments() {
        throw new AssertionError("no profile document is read here");
    }

    public static class Evidence {
        @Tool(description = "Evidence snapshot")
        public String evidence(@ToolParam(required = true, description = "Profile") String profileId) {
            return Json.toString(Json.createObject().put("profileId", profileId));
        }
    }
}
