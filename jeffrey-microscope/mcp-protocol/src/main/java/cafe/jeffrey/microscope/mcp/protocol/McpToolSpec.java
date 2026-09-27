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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code tools/list} description of a single MCP tool: its name, human-readable description,
 * JSON-Schema input definition, and the behavioural hints a client shows before approving a call.
 *
 * @param name        the tool name as seen by the model ({@code mcp__<server>__<name>})
 * @param title       a human-readable display name; null or blank sends none
 * @param description the tool description
 * @param inputSchema the JSON-Schema object describing the tool's arguments
 * @param annotations what the tool does to the world — read-only unless it says otherwise
 * @param outputSchema the JSON-Schema of the structured result, or null for a text-only tool
 * @param meta        what the host reads before calling the tool, rendered as {@code _meta} in the
 *                    order given
 */
public record McpToolSpec(
        String name,
        String title,
        String description,
        ObjectNode inputSchema,
        McpToolAnnotations annotations,
        ObjectNode outputSchema,
        Map<String, JsonNode> meta
) {
    public McpToolSpec {
        meta = meta == null ? Map.of() : orderedCopy(meta);
    }

    /**
     * The meta in the order it was given: tools/list renders it as it iterates, and a hash order would
     * change the bytes a client caches between two runs of the same build. A null key or value would
     * render as a broken entry, so it is refused here, naming the key.
     */
    private static Map<String, JsonNode> orderedCopy(Map<String, JsonNode> meta) {
        Map<String, JsonNode> copy = new LinkedHashMap<>();
        meta.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "meta key"),
                Objects.requireNonNull(value, () -> "meta value of " + key)));
        return Collections.unmodifiableMap(copy);
    }
}
