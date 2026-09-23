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
package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/** A tool's readable answer and, when explicitly supplied, its machine-readable object. */
public record McpToolResult(String text, ObjectNode structuredContent) {

    public McpToolResult {
        Objects.requireNonNull(text, "text");
        if (structuredContent != null) {
            // Copied so the caller cannot change what was measured; the accessor copies again so a
            // reader cannot either. Both are load-bearing, which is why hasStructuredContent() exists
            // and why callers that need the node should take it once -- each read walks a tree that
            // can be 120,000 characters.
            structuredContent = structuredContent.deepCopy();
            if (Json.toString(structuredContent).length() > McpToolOutput.MAX_CHARS) {
                throw new IllegalArgumentException("Structured tool result exceeds the output size limit. "
                        + "Return fewer rows, or narrow the query that produced them.");
            }
        }
    }

    public static McpToolResult text(String text) {
        return new McpToolResult(text, null);
    }

    /** Whether a machine-readable object was supplied, without building a copy to find out. */
    public boolean hasStructuredContent() {
        return structuredContent != null;
    }

    @Override
    public ObjectNode structuredContent() {
        return structuredContent == null ? null : structuredContent.deepCopy();
    }
}
