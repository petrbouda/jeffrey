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

package cafe.jeffrey.jfr.events.trace;

import java.util.Objects;

/**
 * Builds the JSON object string an {@code attributes} field carries — {@link
 * AbstractTracedEvent#attributes} on a span, {@link AbstractTracedInstant#attributes} on an instant
 * — without pulling a JSON library into instrumentation. This library's zero-dependency promise is
 * exactly why emitters were hand-concatenating JSON, and hand-concatenated JSON breaks on the first
 * value containing a quote.
 * <p>
 * Either family's field is encoded identically, so one reader renders both and one index searches
 * both.
 *
 * <pre>{@code
 * event.attributes = EventAttributes.create()
 *         .put("cache", "miss")
 *         .put("retries", 2)
 *         .put("fallback", true)
 *         .json();
 * }</pre>
 *
 * Values are escaped per JSON's rules (quotes, backslashes, control characters), so any string is
 * safe to record — though what belongs in an attribute is still the emitter's judgement: the
 * recording and Jeffrey's profile database contain the values verbatim, so scrub anything
 * sensitive before putting it here.
 * <p>
 * Attributes are operation detail, not identity: the span's <em>name</em> stays low-cardinality,
 * and per-request values (an entity id, a retry count) go here instead. Build the object only
 * when the event actually commits — inside the {@code shouldCommit()} block or a
 * {@code TracedEvents.emit} filler — so an event under threshold pays nothing for it.
 * <p>
 * Keys are written in the order given and are not de-duplicated; give each key once. This builder
 * is single-use and not thread-safe, like the event it fills.
 */
public final class EventAttributes {

    private static final String NULL_LITERAL = "null";

    /** Control characters below this code point must be escaped in JSON strings. */
    private static final char FIRST_PLAIN_CHAR = 0x20;

    private final StringBuilder pairs = new StringBuilder();

    private EventAttributes() {
    }

    public static EventAttributes create() {
        return new EventAttributes();
    }

    /**
     * Records a string value; {@code null} is recorded as JSON {@code null}.
     */
    public EventAttributes put(String key, String value) {
        appendKey(key);
        if (value == null) {
            pairs.append(NULL_LITERAL);
        } else {
            appendQuoted(value);
        }
        return this;
    }

    public EventAttributes put(String key, long value) {
        appendKey(key);
        pairs.append(value);
        return this;
    }

    /**
     * Records a numeric value. JSON has no encoding for {@code NaN} or an infinity, so a
     * non-finite value is recorded as JSON {@code null}.
     */
    public EventAttributes put(String key, double value) {
        appendKey(key);
        if (Double.isFinite(value)) {
            pairs.append(value);
        } else {
            pairs.append(NULL_LITERAL);
        }
        return this;
    }

    public EventAttributes put(String key, boolean value) {
        appendKey(key);
        pairs.append(value);
        return this;
    }

    /**
     * @return the attributes as a JSON object string, {@code "{}"} when nothing was put
     */
    public String json() {
        return "{" + pairs + "}";
    }

    private void appendKey(String key) {
        Objects.requireNonNull(key, "key must not be null");
        if (!pairs.isEmpty()) {
            pairs.append(',');
        }
        appendQuoted(key);
        pairs.append(':');
    }

    private void appendQuoted(String value) {
        pairs.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> pairs.append("\\\"");
                case '\\' -> pairs.append("\\\\");
                case '\b' -> pairs.append("\\b");
                case '\f' -> pairs.append("\\f");
                case '\n' -> pairs.append("\\n");
                case '\r' -> pairs.append("\\r");
                case '\t' -> pairs.append("\\t");
                default -> {
                    if (c < FIRST_PLAIN_CHAR) {
                        pairs.append("\\u%04x".formatted((int) c));
                    } else {
                        pairs.append(c);
                    }
                }
            }
        }
        pairs.append('"');
    }
}
