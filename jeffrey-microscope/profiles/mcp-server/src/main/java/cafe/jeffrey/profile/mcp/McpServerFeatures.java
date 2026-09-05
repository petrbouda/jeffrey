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

import java.util.function.Supplier;

/**
 * What one endpoint offers: its tools, and optionally its prompts and resources.
 * <p>
 * The envelope used to take a supplier of tools alone, which was right while tools were all there was.
 * Prompts and resources are resolved the same lazy way and for the same reason — {@code initialize}
 * and {@code ping} must answer even when building the toolset would fail — so they travel together
 * rather than as three parameters that have to be kept in the same order at every call site.
 * <p>
 * A provider left null is a capability the endpoint does not advertise. The per-profile endpoint the
 * headless CLI uses offers only tools, because a prompt telling a reader which family to start with is
 * meaningless to a client that was handed one profile and one toolset.
 *
 * @param tools     the toolset, resolved per request
 * @param prompts   the prompts, or null when this endpoint offers none
 * @param resources the resources, or null when this endpoint offers none
 */
public record McpServerFeatures(
        Supplier<McpToolProvider> tools,
        Supplier<McpPromptProvider> prompts,
        Supplier<McpResourceProvider> resources) {

    public McpServerFeatures {
        if (tools == null) {
            throw new IllegalArgumentException("tools must not be null");
        }
    }

    /**
     * An endpoint that offers tools and nothing else.
     */
    public static McpServerFeatures ofTools(Supplier<McpToolProvider> tools) {
        return new McpServerFeatures(tools, null, null);
    }

    public boolean hasPrompts() {
        return prompts != null;
    }

    public boolean hasResources() {
        return resources != null;
    }
}
