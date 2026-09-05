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
            NamedArray largest = largestArray(tree);
            if (largest == null || largest.node().isEmpty()) {
                break;
            }
            ArrayNode node = largest.node();
            int before = node.size();
            int keep = Math.max(1, (int) (before * TRIM_RATIO));
            while (node.size() > keep) {
                node.remove(node.size() - 1);
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
     * <p>
     * The name is what makes the record worth carrying. "slowRequests: kept 20 of 500" tells a reader
     * which of the answer's lists they are seeing part of; a bare counter tells them only that
     * something was cut, which they could already see from the field being there at all.
     */
    private static void recordTrim(ObjectNode truncated, NamedArray trimmed, int originalSize) {
        String label = trimmed.name() == null
                ? ARRAY_LABEL_PREFIX + (truncated.size() + 1)
                : trimmed.name();

        // The same list can be the biggest one twice over. Its record is then updated rather than
        // written again: two entries for one field would read as two lists having been cut, and the
        // second one's "original" would be the size it had already been trimmed to.
        ObjectNode entry = truncated.has(label)
                ? (ObjectNode) truncated.get(label)
                : truncated.putObject(label).put(TRUNCATED_ORIGINAL, originalSize);
        entry.put(TRUNCATED_KEPT, trimmed.node().size());
    }

    /**
     * The array holding the most elements anywhere in the tree — the one whose loss buys the most room
     * — together with the field name it sits under, where it has one.
     */
    private static NamedArray largestArray(JsonNode root) {
        NamedArray largest = null;
        Deque<NamedArray> pending = new ArrayDeque<>();
        pending.push(new NamedArray(null, root));
        while (!pending.isEmpty()) {
            NamedArray current = pending.pop();
            JsonNode value = current.value();
            if (value.isArray() && (largest == null || value.size() > largest.node().size())) {
                largest = current;
            }
            if (value.isObject()) {
                // An object names its children; an array does not, so its elements inherit its own
                // name and a trimmed one is still reported under the field a reader can find.
                value.propertyStream()
                        .filter(property -> isContainer(property.getValue()))
                        .forEach(property ->
                                pending.push(new NamedArray(property.getKey(), property.getValue())));
            } else {
                for (JsonNode child : value) {
                    if (isContainer(child)) {
                        pending.push(new NamedArray(current.name(), child));
                    }
                }
            }
        }
        return largest;
    }

    private static boolean isContainer(JsonNode node) {
        return node.isObject() || node.isArray();
    }

    /**
     * A node in the tree, with the object field it hangs off — {@code null} at the root and anywhere
     * an array's own elements are being walked.
     */
    private record NamedArray(String name, JsonNode value) {

        ArrayNode node() {
            return (ArrayNode) value;
        }
    }

    /**
     * A domain answer of "there is nothing here", as opposed to a bad argument — which throws.
     */
    public static String error(String message) {
        return ERROR_PREFIX + message;
    }
}
