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

import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * A tool's readable answer and, when explicitly supplied, its machine-readable object.
 * <p>
 * How large either may be is the server's choice, not the result's: the dispatcher caps the text and
 * refuses structured content past its {@link McpDispatcherSettings#maxResultChars()} with
 * {@link #OVERSIZED_STRUCTURED_CONTENT}, and a server may refuse it earlier, inside the tool call, by
 * asking {@link #exceeds}.
 */
public record McpToolResult(String text, ObjectNode structuredContent) implements McpToolOutcome {

    /** What a caller is told when a result's structured content is past the server's limit. */
    public static final String OVERSIZED_STRUCTURED_CONTENT = "Structured tool result exceeds the output size limit. "
            + "Return fewer rows, or narrow the query that produced them.";

    public McpToolResult {
        Objects.requireNonNull(text, "text");
        if (structuredContent != null) {
            // Copied so the caller cannot change what was measured; the accessor copies again so a
            // reader cannot either. Both are load-bearing, which is why hasStructuredContent() exists
            // and why callers that need the node should take it once -- each read walks a tree that
            // can be as large as the server's result limit.
            structuredContent = structuredContent.deepCopy();
        }
    }

    public static McpToolResult text(String text) {
        return new McpToolResult(text, null);
    }

    /**
     * A result whose text and structured content are both the record: the text is its JSON, written by
     * the shared mapper, and the structured content the same JSON as a tree. Components are written by
     * name and a null as {@code null}, which is what the {@code ["t","null"]} of an
     * {@link McpNullable} component in the generated {@link McpOutputSchema} expects.
     */
    public static McpToolResult of(Record payload) {
        Objects.requireNonNull(payload, "payload");
        ObjectNode structured = (ObjectNode) McpJson.toTree(payload);
        return new McpToolResult(McpJson.toString(structured), structured);
    }

    /**
     * A result that reads differently than it parses: {@code text} for the model — a Markdown table,
     * a sentence — beside the record as structured content.
     */
    public static McpToolResult of(String text, Record payload) {
        Objects.requireNonNull(payload, "payload");
        return new McpToolResult(text, (ObjectNode) McpJson.toTree(payload));
    }

    /** A result is already complete. */
    @Override
    public McpToolResult requireComplete() {
        return this;
    }

    /**
     * Whether the structured content serialises past {@code maxChars}. Measures the held node, without
     * the copy the accessor makes; a text-only result never exceeds.
     */
    public boolean exceeds(int maxChars) {
        return structuredContent != null && McpJson.toString(structuredContent).length() > maxChars;
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
