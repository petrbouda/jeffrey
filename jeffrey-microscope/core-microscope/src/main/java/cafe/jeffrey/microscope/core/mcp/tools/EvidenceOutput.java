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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/** Bounds designated collections by dropping complete records, never fields inside evidence. */
final class EvidenceOutput {

    private static final int METADATA_RESERVE = 8_192;
    private final ObjectNode root;
    private final ObjectNode truncation;
    private final int rowLimit;

    EvidenceOutput(ObjectNode root, int rowLimit) {
        this.root = root;
        this.rowLimit = rowLimit;
        this.truncation = root.putObject("truncation");
        root.put("outputLimitChars", McpToolOutput.MAX_CHARS);
    }

    void rows(String name, List<?> values) {
        rows(root, name, name, values);
    }

    void rows(ObjectNode owner, String name, String path, List<?> values) {
        ArrayNode rows = owner.putArray(name);
        ObjectNode counts = truncation.putObject(path).put("total", values.size());
        int count = Math.min(rowLimit, values.size());
        for (int index = 0; index < count; index++) {
            rows.add(Json.toTree(values.get(index)));
            if (Json.toString(root).length() > McpToolOutput.MAX_CHARS - METADATA_RESERVE) {
                rows.remove(rows.size() - 1);
                break;
            }
        }
        counts.put("returned", rows.size()).put("omitted", values.size() - rows.size());
        counts.put("reason", rows.size() == values.size() ? "complete"
                : rows.size() < count ? "output-size-limit" : "row-limit");
    }

    McpToolResult result() {
        String json = Json.toString(root);
        if (json.length() > McpToolOutput.MAX_CHARS) {
            throw new IllegalArgumentException("Evidence identity metadata exceeds the output budget");
        }
        return new McpToolResult(json, root);
    }
}
