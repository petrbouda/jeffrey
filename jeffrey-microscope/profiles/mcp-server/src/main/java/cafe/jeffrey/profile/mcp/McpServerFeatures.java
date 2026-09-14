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
 * What one endpoint offers: its tools, its prompts, its resources, the orientation it hands a client
 * at {@code initialize}, the argument completions it can answer, and the resource links its tools
 * carry.
 * <p>
 * The envelope used to take a supplier of tools alone, which was right while tools were all there was.
 * Every one of them is resolved the same lazy way and for the same reason: {@code initialize} and
 * {@code ping} must answer even when building the toolset would fail, so they travel together rather
 * than as parameters that have to be kept in the same order at every call site.
 * <p>
 * The three-argument constructor is what an endpoint offering only the original three uses: it
 * declares no instructions, no completions and no links, and the envelope then advertises no
 * {@code completions} capability at all rather than one that refuses everything.
 *
 * @param tools        the toolset, resolved per request
 * @param prompts      the prompts, resolved per request
 * @param resources    the resources, resolved per request
 * @param instructions how to use this server, returned with {@code initialize}; null or blank for none
 * @param completions  the completion provider, resolved per request
 * @param resourceLinks the tool-to-resource mapping, resolved per request
 */
public record McpServerFeatures(
        Supplier<McpToolProvider> tools,
        Supplier<McpPromptProvider> prompts,
        Supplier<McpResourceProvider> resources,
        Supplier<String> instructions,
        Supplier<McpCompletionProvider> completions,
        Supplier<McpResourceLinker> resourceLinks) {

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
        if (instructions == null) {
            throw new IllegalArgumentException("instructions must not be null");
        }
        if (completions == null) {
            throw new IllegalArgumentException("completions must not be null");
        }
        if (resourceLinks == null) {
            throw new IllegalArgumentException("resourceLinks must not be null");
        }
    }

    public McpServerFeatures(
            Supplier<McpToolProvider> tools,
            Supplier<McpPromptProvider> prompts,
            Supplier<McpResourceProvider> resources) {

        this(tools, prompts, resources, () -> null, () -> McpCompletionProvider.NONE, () -> McpResourceLinker.NONE);
    }
}
