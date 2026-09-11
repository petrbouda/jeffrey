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

package cafe.jeffrey.jfr.events.test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a span's {@code attributes} field back into a map, so an assertion can name one key.
 * <p>
 * Hand-rolled because this module depends on nothing by design — the same reason
 * {@code EventAttributes} writes the JSON by hand on the other side. It handles a <em>flat object
 * of scalar values</em> and nothing else, which is not a shortcut: that is exactly the shape
 * Jeffrey's own attribute index accepts, since its flattener drops every key whose value is an
 * object or an array. An attribute this class refuses to read is an attribute no search page and no
 * MCP tool would have found either, so refusing it loudly here is the point.
 * <p>
 * Values come back as text, numbers and booleans included, which is how {@code json_extract_string}
 * hands them to the index as well.
 */
final class FlatJson {

    private static final char OBJECT_START = '{';
    private static final char OBJECT_END = '}';
    private static final char QUOTE = '"';
    private static final char KEY_SEPARATOR = ':';
    private static final char PAIR_SEPARATOR = ',';
    private static final char ESCAPE = '\\';
    private static final char UNICODE_ESCAPE = 'u';

    private static final int UNICODE_DIGITS = 4;
    private static final int HEX_RADIX = 16;

    private final String json;
    private int position;

    private FlatJson(String json) {
        this.json = json;
    }

    /**
     * @param json the raw field, which is {@code null} for a span that attached nothing
     * @return the pairs in the order they were written, or an empty map when there were none
     * @throws IllegalArgumentException when the text is not a flat JSON object of scalars
     */
    static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return new FlatJson(json).object();
    }

    private Map<String, String> object() {
        Map<String, String> pairs = new LinkedHashMap<>();

        expect(OBJECT_START);
        skipWhitespace();
        if (peek() == OBJECT_END) {
            return pairs;
        }

        while (true) {
            skipWhitespace();
            String key = string();
            skipWhitespace();
            expect(KEY_SEPARATOR);
            skipWhitespace();
            pairs.put(key, value());
            skipWhitespace();

            char separator = next();
            if (separator == OBJECT_END) {
                return pairs;
            }
            if (separator != PAIR_SEPARATOR) {
                throw fail("expected ',' or '}' but found '" + separator + "'");
            }
        }
    }

    private String value() {
        char start = peek();
        if (start == QUOTE) {
            return string();
        }
        if (start == OBJECT_START || start == '[') {
            throw fail("a nested object or array is not a value Jeffrey's attribute index records - "
                    + "attributes must be a flat map of scalars");
        }

        int from = position;
        while (position < json.length()) {
            char current = json.charAt(position);
            if (current == PAIR_SEPARATOR || current == OBJECT_END || Character.isWhitespace(current)) {
                break;
            }
            position++;
        }
        if (position == from) {
            throw fail("expected a value");
        }
        return json.substring(from, position);
    }

    private String string() {
        expect(QUOTE);

        StringBuilder text = new StringBuilder();
        while (true) {
            char current = next();
            if (current == QUOTE) {
                return text.toString();
            }
            if (current != ESCAPE) {
                text.append(current);
                continue;
            }
            text.append(unescape(next()));
        }
    }

    private char unescape(char escaped) {
        return switch (escaped) {
            case QUOTE, ESCAPE, '/' -> escaped;
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case UNICODE_ESCAPE -> unicode();
            default -> throw fail("unknown escape '\\" + escaped + "'");
        };
    }

    private char unicode() {
        if (position + UNICODE_DIGITS > json.length()) {
            throw fail("truncated unicode escape");
        }
        String digits = json.substring(position, position + UNICODE_DIGITS);
        position += UNICODE_DIGITS;
        return (char) Integer.parseInt(digits, HEX_RADIX);
    }

    private void skipWhitespace() {
        while (position < json.length() && Character.isWhitespace(json.charAt(position))) {
            position++;
        }
    }

    private void expect(char expected) {
        char actual = next();
        if (actual != expected) {
            throw fail("expected '" + expected + "' but found '" + actual + "'");
        }
    }

    private char peek() {
        if (position >= json.length()) {
            throw fail("unexpected end of input");
        }
        return json.charAt(position);
    }

    private char next() {
        char current = peek();
        position++;
        return current;
    }

    private IllegalArgumentException fail(String reason) {
        return new IllegalArgumentException(
                "cannot read span attributes at offset " + position + ": " + reason + " - in " + json);
    }
}
