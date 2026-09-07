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
package cafe.jeffrey.profile.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one endpoint offers. A null provider is how an endpoint says it does not offer a capability,
 * so the nullability here is the contract rather than an oversight — and the envelope reads it to
 * decide what {@code initialize} advertises.
 */
class McpServerFeaturesTest {

    private static final McpToolProvider TOOLSET =
            new ReflectiveToolset(new Tools(), "sample");

    @Test
    void anEndpointWithToolsAloneAdvertisesNothingElse() {
        McpServerFeatures features = McpServerFeatures.ofTools(() -> TOOLSET);

        assertFalse(features.hasPrompts());
        assertFalse(features.hasResources());
        assertEquals(1, features.tools().get().specs().size());
    }

    @Test
    void carriesPromptsAndResourcesWhenAnEndpointHasThem() {
        McpServerFeatures features = new McpServerFeatures(
                () -> TOOLSET,
                () -> new Prompts(),
                () -> new Resources());

        assertTrue(features.hasPrompts());
        assertTrue(features.hasResources());
    }

    /**
     * Tools are the one thing every endpoint has. A features record without them describes nothing
     * that could serve a request, so it refuses to exist rather than failing at the first call.
     */
    @Test
    void refusesToDescribeAnEndpointWithNoTools() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpServerFeatures(null, null, null));
    }

    /**
     * The suppliers are resolved per request, not here: building the toolset can fail for a profile
     * that has gone, and that must not stop the endpoint answering initialize.
     */
    @Test
    void doesNotResolveItsProvidersOnConstruction() {
        McpServerFeatures features = McpServerFeatures.ofTools(() -> {
            throw new IllegalStateException("resolved too early");
        });

        assertThrows(IllegalStateException.class, () -> features.tools().get());
    }

    public static class Tools {

        @Tool(description = "Ping")
        public String ping() {
            return "pong";
        }
    }

    private static final class Prompts implements McpPromptProvider {

        @Override
        public List<McpPrompt> prompts() {
            return List.of();
        }

        @Override
        public McpPrompt prompt(String name) {
            throw new IllegalArgumentException("Unknown prompt: " + name);
        }
    }

    private static final class Resources implements McpResourceProvider {

        @Override
        public List<McpResource> resources() {
            return List.of();
        }

        @Override
        public List<McpResource> templates() {
            return List.of();
        }

        @Override
        public Contents read(String uri) {
            throw new IllegalArgumentException("Unknown resource: " + uri);
        }
    }
}
