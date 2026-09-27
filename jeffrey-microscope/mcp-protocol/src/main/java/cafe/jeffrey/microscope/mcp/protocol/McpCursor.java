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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/**
 * The one continuation a paged tool hands out: {@code cursor} in, {@code nextCursor} and
 * {@code hasMore} out.
 * <p>
 * A cursor is opaque to the caller and strict to this server. It is base64url JSON holding a
 * {@code version}, a SHA-256 fingerprint of the filters the page was read with, and a position —
 * a {@link Keyset} after the last returned row, or an {@link Offset}. Reading one back refuses it,
 * with a message that says to start again without a cursor, when it does not parse, was written by
 * another version, belongs to other filters or another tool, or is not spelled exactly as it was
 * handed out. The fingerprint binds a cursor to its filters and is not a signature: a hand-written
 * position for the same filters reads only rows those filters already select.
 * <p>
 * It lives beside {@link McpToolResult} rather than with any one tool: the paging contract is the
 * same for every tool that can continue, and it carries no domain type. One codec per server, named
 * after it: a cursor from another version is refused in words that name the server that wrote it.
 */
public final class McpCursor {

    /**
     * The longest cursor this server hands out or reads back. A position is a handful of ids, so
     * this is generous; it keeps a pasted blob from being decoded at all.
     */
    public static final int MAX_CURSOR_CHARS = 4_096;

    private static final int VERSION = 1;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_FILTERS = "filters";
    private static final String FIELD_AFTER = "after";
    private static final String FIELD_OFFSET = "offset";
    private static final int FIELDS = 3;

    private static final String MALFORMED =
            "Invalid cursor: pass nextCursor exactly as it was returned, or omit cursor to start again.";
    private static final String WRONG_VERSION =
            "This cursor was written by another version of %s: omit cursor to start again.";
    private static final String CHANGED_FILTERS =
            "This cursor belongs to other filters: repeat the filters it was returned for, "
                    + "or omit cursor to start again.";

    /** Where the next page starts. */
    public sealed interface Position permits Keyset, Offset {
    }

    /**
     * After the last returned row, named by the values the rows sort on, in sort order. An element
     * is {@code null} where that row has no value for its sort key.
     */
    public record Keyset(List<String> after) implements Position {

        public Keyset {
            if (after == null || after.isEmpty()) {
                throw new IllegalArgumentException("a keyset needs at least one value: after=" + after);
            }
            after = Collections.unmodifiableList(new ArrayList<>(after));
        }
    }

    /** The number of rows the earlier pages returned. */
    public record Offset(int offset) implements Position {

        public Offset {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must not be negative: offset=" + offset);
            }
        }
    }

    /**
     * The filters a cursor is bound to, reduced to their fingerprint: the tool name and the
     * normalised filter values, in a fixed order. A value is {@code null}, a {@code String}, an
     * {@code Integer}, a {@code Long}, a {@code Boolean} or an enum, written as its constant name;
     * anything else has no one spelling the fingerprint could rely on and is refused.
     */
    public record Filters(String fingerprint) {

        public Filters {
            if (fingerprint == null || fingerprint.isBlank()) {
                throw new IllegalArgumentException("fingerprint must not be blank");
            }
        }

        public static Filters of(String tool, Object... values) {
            if (tool == null || tool.isBlank()) {
                throw new IllegalArgumentException("tool must not be blank: tool=" + tool);
            }
            ArrayNode inputs = McpJson.createArray().add(tool);
            for (Object value : values) {
                inputs.add(canonical(value));
            }
            return new Filters(sha256(McpJson.toString(inputs)));
        }

        private static JsonNode canonical(Object value) {
            return switch (value) {
                case null -> NullNode.getInstance();
                case String text -> StringNode.valueOf(text);
                case Integer number -> LongNode.valueOf(number);
                case Long number -> LongNode.valueOf(number);
                case Boolean flag -> BooleanNode.valueOf(flag);
                case Enum<?> constant -> StringNode.valueOf(constant.name());
                default -> throw new IllegalArgumentException(
                        "a cursor filter is null, String, Integer, Long, Boolean or an enum: type="
                                + value.getClass().getName());
            };
        }
    }

    /**
     * What a page says about continuing: whether rows remain, and the cursor that reads them.
     */
    public record Next(boolean hasMore, String nextCursor) {

        public static final Next END = new Next(false, null);

        public Next {
            if (hasMore != (nextCursor != null)) {
                throw new IllegalArgumentException(
                        "hasMore and nextCursor must agree: hasMore=" + hasMore + " nextCursor=" + nextCursor);
            }
        }
    }

    private final String wrongVersion;

    /**
     * @param writer the server that hands the cursors out, as a refused cursor names it
     */
    public McpCursor(String writer) {
        if (writer == null || writer.isBlank()) {
            throw new IllegalArgumentException("A cursor codec needs the name of the server that writes it");
        }
        this.wrongVersion = WRONG_VERSION.formatted(writer);
    }

    public String encode(Filters filters, Position position) {
        ObjectNode value = McpJson.createObject();
        value.put(FIELD_VERSION, VERSION);
        value.put(FIELD_FILTERS, filters.fingerprint());
        switch (position) {
            case Keyset keyset -> {
                ArrayNode after = value.putArray(FIELD_AFTER);
                keyset.after().forEach(after::add);
            }
            case Offset offset -> value.put(FIELD_OFFSET, offset.offset());
        }
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(McpJson.toByteArray(value));
        if (cursor.length() > MAX_CURSOR_CHARS) {
            throw new IllegalArgumentException("position too large for a cursor: chars=" + cursor.length()
                    + " max=" + MAX_CURSOR_CHARS);
        }
        return cursor;
    }

    /**
     * The position a cursor handed out for these filters holds.
     *
     * @throws IllegalArgumentException when the cursor is malformed, of another version, or bound to
     *                                  other filters; the message says to start again without one
     */
    public Position decode(String cursor, Filters filters) {
        ObjectNode value = parse(cursor);
        JsonNode version = value.get(FIELD_VERSION);
        if (version == null || !version.isInt()) {
            throw malformed(null);
        }
        if (version.asInt() != VERSION) {
            throw new IllegalArgumentException(wrongVersion);
        }
        JsonNode fingerprint = value.get(FIELD_FILTERS);
        if (value.size() != FIELDS || fingerprint == null || !fingerprint.isString()) {
            throw malformed(null);
        }
        Position position = position(value);
        if (!filters.fingerprint().equals(fingerprint.asString())) {
            throw new IllegalArgumentException(CHANGED_FILTERS);
        }
        if (!encode(filters, position).equals(cursor)) {
            throw malformed(null);
        }
        return position;
    }

    /**
     * A keyset cursor, read into the tool's own key. A cursor holding an offset, or a keyset the
     * reader refuses by throwing, is malformed.
     */
    public <T> T decodeKeyset(String cursor, Filters filters, Function<Keyset, T> reader) {
        if (!(decode(cursor, filters) instanceof Keyset keyset)) {
            throw malformed(null);
        }
        try {
            return reader.apply(keyset);
        } catch (RuntimeException e) {
            throw malformed(e);
        }
    }

    /**
     * An offset cursor's offset. A cursor holding a keyset is malformed.
     */
    public int decodeOffset(String cursor, Filters filters) {
        if (!(decode(cursor, filters) instanceof Offset offset)) {
            throw malformed(null);
        }
        return offset.offset();
    }

    /**
     * Whether an offset-based page continues, and the cursor that reads the rest.
     *
     * @param offset   where this page started
     * @param returned the rows this page returned
     * @param total    the rows the filters match now; a list that shrank below the offset ends
     * @throws IllegalArgumentException when rows remain but none were returned, which would hand the
     *                                  same cursor back forever
     */
    public Next nextOffset(Filters filters, int offset, int returned, int total) {
        if (offset < 0 || returned < 0 || total < 0) {
            throw new IllegalArgumentException("offset, returned and total must not be negative: offset="
                    + offset + " returned=" + returned + " total=" + total);
        }
        int end = Math.addExact(offset, returned);
        if (end >= total) {
            return Next.END;
        }
        if (returned == 0) {
            throw new IllegalArgumentException(
                    "a page that returned no rows cannot continue: offset=" + offset + " total=" + total);
        }
        return new Next(true, encode(filters, new Offset(end)));
    }

    private static ObjectNode parse(String cursor) {
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_CURSOR_CHARS) {
            throw malformed(null);
        }
        JsonNode value;
        try {
            value = McpJson.readTree(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw malformed(e);
        }
        if (value == null || !value.isObject()) {
            throw malformed(null);
        }
        return (ObjectNode) value;
    }

    private static Position position(ObjectNode value) {
        JsonNode after = value.get(FIELD_AFTER);
        JsonNode offset = value.get(FIELD_OFFSET);
        if (after != null && after.isArray() && !after.isEmpty()) {
            List<String> values = new ArrayList<>(after.size());
            for (JsonNode element : after) {
                if (!element.isNull() && !element.isString()) {
                    throw malformed(null);
                }
                values.add(element.isNull() ? null : element.asString());
            }
            return new Keyset(values);
        }
        if (offset != null && offset.isInt() && offset.asInt() >= 0) {
            return new Offset(offset.asInt());
        }
        throw malformed(null);
    }

    private static IllegalArgumentException malformed(Throwable cause) {
        return new IllegalArgumentException(MALFORMED, cause);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required cursor hash is unavailable: algorithm=" + HASH_ALGORITHM, e);
        }
    }
}
