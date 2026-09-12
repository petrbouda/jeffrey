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

    /** Where the record of what was trimmed is attached on a JSON answer that had to lose rows. */
    private static final String TRUNCATED_FIELD = "_truncated";

    /**
     * Where a top-level array goes when it had to be trimmed. A record of the loss can only be attached
     * to an object, and a list handed back short with nothing saying so is the silent truncation this
     * class exists to prevent.
     */
    private static final String WRAPPED_ARRAY_FIELD = "items";
    private static final String TRUNCATED_KEPT = "kept";
    private static final String TRUNCATED_ORIGINAL = "original";
    private static final String TRUNCATED_LIMIT = "limit";
    private static final String TRUNCATED_REASON = "reason";
    private static final String UNTRIMMABLE_JSON_REASON =
            "JSON result exceeded the character limit and could not be trimmed structurally";
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
     * <p>
     * A value that is itself an array comes back unchanged while it fits, and as
     * {@code {"items": [...], "_truncated": {...}}} when it did not: the record has to hang off an
     * object, and a shortened list with nothing saying so is the case this method exists for.
     */
    public static String json(Object value) {
        String rendered = Json.toString(value);
        if (rendered.length() <= MAX_CHARS) {
            return rendered;
        }
        JsonNode tree = Json.toTree(value);
        if (!tree.isObject() && !tree.isArray()) {
            return overflowJson(rendered.length());
        }
        return trimToFit(tree, rendered.length());
    }

    /**
     * Shortens the biggest array in the tree until the whole thing fits, recording what it took.
     * <p>
     * The record of what was lost is attached <em>before</em> the size is judged, not after. Attaching
     * it afterwards is what made this method able to return the one thing it exists to prevent: the
     * loop would stop at the cap, the record would push the rendering back over it, and {@code capped}
     * would then cut the JSON mid-token and append a Markdown sentence to it.
     * <p>
     * A bare array is answered as an object wrapping it, for the same reason. The record can only hang
     * off an object, and a list silently returned short is exactly the failure this class is for.
     */
    private static String trimToFit(JsonNode tree, int originalChars) {
        ObjectNode truncated = Json.createObject();
        for (int pass = 0; pass < MAX_TRIM_PASSES; pass++) {
            String rendered = render(tree, truncated);
            if (rendered.length() <= MAX_CHARS) {
                return rendered;
            }
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
        }
        // A scalar-heavy tree, or an array whose last element is itself too large, cannot be shortened
        // by dropping rows. Return a compact JSON record rather than cutting a JSON token and appending
        // the Markdown truncation note used by capped(String).
        return overflowJson(originalChars);
    }

    private static String overflowJson(int originalChars) {
        ObjectNode root = Json.createObject();
        ObjectNode truncated = root.putObject(TRUNCATED_FIELD);
        truncated.put(TRUNCATED_REASON, UNTRIMMABLE_JSON_REASON);
        truncated.put(TRUNCATED_ORIGINAL, originalChars);
        truncated.put(TRUNCATED_LIMIT, MAX_CHARS);
        return Json.toString(root);
    }

    /**
     * Renders the tree with the record of what was trimmed attached, wrapping a bare array in an object
     * so the record has somewhere to hang.
     */
    private static String render(JsonNode tree, ObjectNode truncated) {
        if (truncated.isEmpty()) {
            return Json.toString(tree);
        }
        if (tree.isObject()) {
            ((ObjectNode) tree).set(TRUNCATED_FIELD, truncated);
            return Json.toString(tree);
        }
        ObjectNode wrapper = Json.createObject();
        wrapper.set(WRAPPED_ARRAY_FIELD, tree);
        wrapper.set(TRUNCATED_FIELD, truncated);
        return Json.toString(wrapper);
    }

    /**
     * Notes one trimmed array under its path from the root, falling back to a counter for the root array.
     * <p>
     * The name is what makes the record worth carrying. "slowRequests: kept 20 of 500" tells a reader
     * which of the answer's lists they are seeing part of; a bare counter tells them only that
     * something was cut, which they could already see from the field being there at all.
     */
    private static void recordTrim(ObjectNode truncated, NamedArray trimmed, int originalSize) {
        String label = trimmed.path() == null
                ? ARRAY_LABEL_PREFIX + 1
                : trimmed.path();

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
     * — together with its path from the root, where it has one.
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
                value.propertyStream()
                        .filter(property -> isContainer(property.getValue()))
                        .forEach(property ->
                                pending.push(new NamedArray(
                                        appendProperty(current.path(), property.getKey()),
                                        property.getValue())));
            } else {
                int index = 0;
                for (JsonNode child : value) {
                    if (isContainer(child)) {
                        pending.push(new NamedArray(appendIndex(current.path(), index), child));
                    }
                    index++;
                }
            }
        }
        return largest;
    }

    private static String appendProperty(String path, String property) {
        String segment = isPlainPathSegment(property)
                ? property
                : "[" + Json.toString(property) + "]";
        if (path == null) {
            return segment;
        }
        return segment.startsWith("[") ? path + segment : path + "." + segment;
    }

    private static String appendIndex(String path, int index) {
        return (path == null ? "" : path) + "[" + index + "]";
    }

    private static boolean isPlainPathSegment(String property) {
        if (property.isEmpty()
                || !(Character.isLetter(property.charAt(0)) || property.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < property.length(); i++) {
            char c = property.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isContainer(JsonNode node) {
        return node.isObject() || node.isArray();
    }

    /**
     * A node in the tree, with its unambiguous path from the root ({@code null} for the root itself).
     */
    private record NamedArray(String path, JsonNode value) {

        ArrayNode node() {
            return (ArrayNode) value;
        }
    }

    /** A tool failure that the MCP envelope must mark with {@code isError=true}. */
    public static String error(String message) {
        throw new ToolExecutionException(message);
    }
}
