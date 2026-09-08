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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What one endpoint offers. All three providers are required and resolved per request, so an endpoint
 * that cannot serve one of them is refused at construction rather than at the first call.
 */
class McpServerFeaturesTest {

    private static final McpToolProvider TOOLSET =
            new ReflectiveToolset(new Tools(), "sample");

    @Test
    void carriesEveryCapabilityAnEndpointOffers() {
        McpServerFeatures features = new McpServerFeatures(
                () -> TOOLSET,
                Prompts::new,
                Resources::new);

        assertEquals(1, features.tools().get().specs().size());
        assertNotNull(features.prompts().get());
        assertNotNull(features.resources().get());
    }

    /**
     * A features record missing any of the three describes an endpoint that cannot answer a method the
     * envelope will advertise, so it refuses to exist rather than failing at the first call.
     */
    @Test
    void refusesToDescribeAnEndpointMissingACapability() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpServerFeatures(null, Prompts::new, Resources::new));
        assertThrows(IllegalArgumentException.class,
                () -> new McpServerFeatures(() -> TOOLSET, null, Resources::new));
        assertThrows(IllegalArgumentException.class,
                () -> new McpServerFeatures(() -> TOOLSET, Prompts::new, null));
    }

    /**
     * The suppliers are resolved per request, not here: building the toolset can fail for a profile
     * that has gone, and that must not stop the endpoint answering initialize.
     */
    @Test
    void doesNotResolveItsProvidersOnConstruction() {
        McpServerFeatures features = new McpServerFeatures(
                () -> {
                    throw new IllegalStateException("resolved too early");
                },
                Prompts::new,
                Resources::new);

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
