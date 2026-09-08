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
 * What one endpoint offers: its tools, its prompts and its resources.
 * <p>
 * The envelope used to take a supplier of tools alone, which was right while tools were all there was.
 * All three are resolved the same lazy way and for the same reason: {@code initialize} and {@code ping}
 * must answer even when building the toolset would fail, so they travel together rather than as three
 * parameters that have to be kept in the same order at every call site.
 *
 * @param tools     the toolset, resolved per request
 * @param prompts   the prompts, resolved per request
 * @param resources the resources, resolved per request
 */
public record McpServerFeatures(
        Supplier<McpToolProvider> tools,
        Supplier<McpPromptProvider> prompts,
        Supplier<McpResourceProvider> resources) {

    public McpServerFeatures {
        if (tools == null) {
            throw new IllegalArgumentException("tools must not be null");
        }
        if (prompts == null) {
            throw new IllegalArgumentException("prompts must not be null");
        }
        if (resources == null) {
            throw new IllegalArgumentException("resources must not be null");
        }
    }
}
