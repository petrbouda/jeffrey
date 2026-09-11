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

package cafe.jeffrey.jfr.events.servlet;

import cafe.jeffrey.jfr.events.trace.AttributeValues;
import cafe.jeffrey.jfr.events.trace.EventAttributes;

import java.util.HashSet;
import java.util.Set;

/**
 * What a {@link HttpExchangeAttributesCustomizer} writes the request's own detail into.
 * <p>
 * A wrapper around {@link EventAttributes} rather than the builder itself, because what Jeffrey's
 * attribute index requires of an attribute is best guaranteed <em>here</em>, on the way in. That
 * index flattens the recorded map one row per key and silently drops any key containing a quote and
 * any empty value, so those are refused or skipped at the point they are written — an attribute
 * that reaches the recording and can never be searched is the worst of the outcomes.
 * <p>
 * A collection or a map is <em>not</em> refused: it is recorded as its own text
 * ({@code {a=1, b=2}}), which is searchable only as that exact string and is almost never what the
 * caller meant. Attributes are a flat map of scalars; put each value under its own key.
 * <p>
 * Not thread-safe, and not meant to be: one is created per exchange and every customizer runs on
 * the thread completing it.
 */
public final class HttpExchangeAttributes {

    private static final char KEY_QUOTE = '"';

    private final EventAttributes attributes = EventAttributes.create();
    private final Set<String> keys = new HashSet<>();

    /**
     * The filter creates one of these per exchange; it is public so that an application can create
     * one in a test of its own customizer, which is otherwise not reachable from outside.
     */
    public HttpExchangeAttributes() {
    }

    /**
     * Attaches one key to the exchange's span.
     * <p>
     * A {@code null} value, or one whose text is blank, records nothing at all: the index drops
     * both, so writing them costs payload bytes for a key no search would ever match. A key already
     * written is kept as it was — the underlying builder does not de-duplicate, and a repeated key
     * would become two rows in the index and two entries in a facet count.
     *
     * @param key   the attribute's name; dots are welcome and read well, quotes are not allowed
     * @param value a scalar — text, a number or a boolean; anything else is recorded as its text
     * @return this, so contributions chain
     * @throws IllegalArgumentException when the key is missing, blank, or contains a quote
     */
    public HttpExchangeAttributes put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("an attribute key must not be null or blank");
        }
        if (key.indexOf(KEY_QUOTE) >= 0) {
            throw new IllegalArgumentException(
                    "an attribute key must not contain a quote, because Jeffrey's attribute index "
                            + "cannot address such a key and would drop it silently: key=" + key);
        }
        if (value == null || AttributeValues.text(value).isBlank()) {
            return this;
        }
        if (!keys.add(key)) {
            return this;
        }

        AttributeValues.put(attributes, key, value);
        return this;
    }

    /**
     * @return whether nothing was attached, which is what leaves the event's field absent rather
     * than recording an empty object
     */
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /**
     * @return the JSON object to record — what a span's {@code attributes} field will hold, and so
     * what a test of a customizer asserts on
     */
    public String json() {
        return attributes.json();
    }
}
