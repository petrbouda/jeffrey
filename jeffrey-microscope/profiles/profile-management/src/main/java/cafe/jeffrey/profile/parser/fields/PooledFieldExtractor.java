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

package cafe.jeffrey.profile.parser.fields;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Picks the one field of an event's JSON worth pooling: the largest string value at or over the
 * size threshold. Purely size-based — no field name is known here — so a statement's SQL, a
 * written file's path and a thread dump's result all qualify by the same rule, and an event type
 * instrumented tomorrow takes part with no change on this side.
 * <p>
 * One field per event, the largest: in every recording observed, one field carries an order of
 * magnitude more text than the rest, and a single (key, text) pair is what keeps the storage
 * schema and the read-time splice trivial. The runners-up stay inline, which costs their
 * repetition but decides nothing for correctness.
 */
public final class PooledFieldExtractor {

    /**
     * Below this size, inline storage beats pooling: the reference and the dictionary row cost
     * more to store and to join than the repeated short text costs to keep. At or above it, a
     * repeated value compresses to one row and a value that never repeats costs only its
     * reference.
     */
    private static final int MIN_POOLED_LENGTH = 64;

    /** The field lifted out of the JSON: which key carried it and the text it held. */
    public record PooledValue(String field, String text) {
    }

    private PooledFieldExtractor() {
    }

    /**
     * Removes the largest poolable string field from the given JSON and returns it, or returns
     * {@code null} — leaving the JSON untouched — when no string value reaches the threshold.
     *
     * @param eventFields the event's mapped fields, mutated when something qualifies
     */
    public static PooledValue extractLargest(ObjectNode eventFields) {
        String largestField = null;
        String largestText = null;
        for (Map.Entry<String, JsonNode> property : eventFields.properties()) {
            JsonNode value = property.getValue();
            if (!value.isString()) {
                continue;
            }
            String text = value.asString();
            if (text.length() < MIN_POOLED_LENGTH) {
                continue;
            }
            if (largestText == null || text.length() > largestText.length()) {
                largestField = property.getKey();
                largestText = text;
            }
        }

        if (largestField == null) {
            return null;
        }
        eventFields.remove(largestField);
        return new PooledValue(largestField, largestText);
    }
}
