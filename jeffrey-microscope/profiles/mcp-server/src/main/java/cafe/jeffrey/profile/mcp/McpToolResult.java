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

import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/** A tool's readable answer and, when explicitly supplied, its machine-readable object. */
public record McpToolResult(String text, ObjectNode structuredContent) {

    public McpToolResult {
        Objects.requireNonNull(text, "text");
        if (structuredContent != null) {
            structuredContent = structuredContent.deepCopy();
            if (text.length() > McpToolOutput.MAX_CHARS
                    || Json.toString(structuredContent).length() > McpToolOutput.MAX_CHARS) {
                throw new IllegalArgumentException("Structured tool result exceeds the output size limit");
            }
        }
    }

    public static McpToolResult text(String text) {
        return new McpToolResult(text, null);
    }

    @Override
    public ObjectNode structuredContent() {
        return structuredContent == null ? null : structuredContent.deepCopy();
    }
}
