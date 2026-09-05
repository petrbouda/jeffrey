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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Renders what a tool hands back to the model, and says so when it had to cut.
 * <p>
 * Silent truncation is the failure mode this exists to prevent: a capped list looks exactly like a
 * complete one, and a model that cannot see the cap reports the visible part as the whole story. Every
 * result that hit the ceiling therefore says so — a Markdown answer ends with a line naming the cap,
 * and a JSON one is trimmed in the tree and carries a record of what it lost, so that it stays
 * parseable rather than ending mid-token.
 */
public final class McpToolOutput {

    /**
     * The most any single tool result may carry. Sized well under the point where a client spills the
     * result to a file, so a normal answer stays inline and readable.
     */
    public static final int MAX_CHARS = 120_000;

    private static final String TRUNCATION_NOTE =
            "\n\n_TRUNCATED: the result exceeded %d characters and was cut here. "
                    + "Narrow the query — a smaller limit, a time range, or a more specific filter._";

    private static final String ERROR_PREFIX = "Error: ";

    /** Where the record of what was trimmed is attached on a JSON answer that had to lose rows. */
    private static final String TRUNCATED_FIELD = "_truncated";
    private static final String TRUNCATED_KEPT = "kept";
    private static final String TRUNCATED_ORIGINAL = "original";
    private static final String ARRAY_LABEL_PREFIX = "array";

    /** How much of an oversized array survives one pass. */
    private static final double TRIM_RATIO = 0.5;

    /**
     * How many arrays may be shortened before the result is simply cut. A bound rather than a loop
     * without one: a pathological tree of thousands of tiny arrays would otherwise trim forever without
     * ever reclaiming enough room.
     */
    private static final int MAX_TRIM_PASSES = 12;

    private McpToolOutput() {
    }

    /**
     * Caps a rendered result, appending an explicit note when anything was dropped.
     */
    public static String capped(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + TRUNCATION_NOTE.formatted(MAX_CHARS);
    }

    /**
     * A value rendered as JSON for the model, trimmed to fit rather than cut to fit.
     * <p>
     * Cutting a serialised object at a character count leaves a string that is no longer JSON — it ends
     * mid-token, and a client that parses tool results gets a syntax error where it expected an answer.
     * So the trimming happens in the tree instead: the largest array is shortened, repeatedly if need be,
     * until the rendered form fits. What comes back is always parseable, and it carries a
     * {@code _truncated} entry naming each array that lost rows and how many it had, so a reader can see
     * that it is looking at part of a list rather than a short one.
     */
    public static String json(Object value) {
        String rendered = Json.toString(value);
        if (rendered.length() <= MAX_CHARS) {
            return rendered;
        }
        JsonNode tree = Json.toTree(value);
        if (!tree.isObject() && !tree.isArray()) {
            // A bare scalar cannot be trimmed structurally; it can only be cut.
            return capped(rendered);
        }
        return trimToFit(tree);
    }

    /**
     * Shortens the biggest array in the tree until the whole thing fits, recording what it took.
     */
    private static String trimToFit(JsonNode tree) {
        ObjectNode truncated = Json.createObject();
        String rendered = Json.toString(tree);
        for (int pass = 0; pass < MAX_TRIM_PASSES && rendered.length() > MAX_CHARS; pass++) {
            ArrayNode largest = largestArray(tree);
            if (largest == null || largest.isEmpty()) {
                break;
            }
            int before = largest.size();
            int keep = Math.max(1, (int) (before * TRIM_RATIO));
            while (largest.size() > keep) {
                largest.remove(largest.size() - 1);
            }
            recordTrim(truncated, largest, before);
            rendered = Json.toString(tree);
        }
        if (!truncated.isEmpty() && tree.isObject()) {
            ((ObjectNode) tree).set(TRUNCATED_FIELD, truncated);
            rendered = Json.toString(tree);
        }
        // A tree of scalars can still overrun what any array trimming could reclaim.
        return capped(rendered);
    }

    /**
     * Notes one trimmed array under the name of the field holding it, falling back to a counter when the
     * array is nested somewhere without a name of its own.
     */
    private static void recordTrim(ObjectNode truncated, ArrayNode trimmed, int originalSize) {
        String label = ARRAY_LABEL_PREFIX + (truncated.size() + 1);
        ObjectNode entry = truncated.putObject(label);
        entry.put(TRUNCATED_KEPT, trimmed.size());
        entry.put(TRUNCATED_ORIGINAL, originalSize);
    }

    /**
     * The array holding the most elements anywhere in the tree — the one whose loss buys the most room.
     */
    private static ArrayNode largestArray(JsonNode node) {
        ArrayNode largest = null;
        Deque<JsonNode> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            JsonNode current = pending.pop();
            if (current.isArray() && (largest == null || current.size() > largest.size())) {
                largest = (ArrayNode) current;
            }
            for (JsonNode child : current) {
                if (child.isArray() || child.isObject()) {
                    pending.push(child);
                }
            }
        }
        return largest;
    }

    /**
     * A domain answer of "there is nothing here", as opposed to a bad argument — which throws.
     */
    public static String error(String message) {
        return ERROR_PREFIX + message;
    }
}
