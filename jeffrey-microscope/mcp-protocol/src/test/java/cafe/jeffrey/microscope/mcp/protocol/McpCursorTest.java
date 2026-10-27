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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCursorTest {

    /** A codec as a server named {@code Example} would build it. */
    private static final McpCursor CURSOR = new McpCursor("Example");

    private static final String TOOL = "profiles_list";
    private static final String START_AGAIN = "omit cursor to start again";
    private static final McpCursor.Filters FILTERS = McpCursor.Filters.of(TOOL, "checkout", null, 60);
    private static final McpCursor.Keyset AFTER = new McpCursor.Keyset(Arrays.asList("p-7", null));

    private enum Mode {
        CPU
    }

    @Nested
    class RoundTrip {

        @Test
        void aKeysetComesBackAsItWasEncoded() {
            String cursor = CURSOR.encode(FILTERS, AFTER);

            assertEquals(AFTER, CURSOR.decode(cursor, FILTERS));
        }

        @Test
        void anOffsetComesBackAsItWasEncoded() {
            String cursor = CURSOR.encode(FILTERS, new McpCursor.Offset(100));

            assertEquals(new McpCursor.Offset(100), CURSOR.decode(cursor, FILTERS));
            assertEquals(100, CURSOR.decodeOffset(cursor, FILTERS));
        }

        @Test
        void isBase64UrlWithoutPadding() {
            String cursor = CURSOR.encode(FILTERS, AFTER);

            assertTrue(cursor.matches("[A-Za-z0-9_-]+"), cursor);
        }

        @Test
        void aKeysetReaderTurnsThePositionIntoTheToolsOwnKey() {
            String cursor = CURSOR.encode(FILTERS, AFTER);

            assertEquals("p-7", CURSOR.decodeKeyset(cursor, FILTERS, keyset -> keyset.after().getFirst()));
        }

        @Test
        void equalFiltersGiveEqualFingerprints() {
            assertEquals(FILTERS, McpCursor.Filters.of(TOOL, "checkout", null, 60));
        }
    }

    @Nested
    class FilterValues {

        /** Position matters: the same values in another order are other filters. */
        @Test
        void areOrderSensitive() {
            assertNotEquals(McpCursor.Filters.of(TOOL, "a", null), McpCursor.Filters.of(TOOL, null, "a"));
        }

        @Test
        void acceptTheCanonicalScalarTypes() {
            McpCursor.Filters filters = McpCursor.Filters.of(TOOL, null, "text", 7, 7L, true, Mode.CPU);

            assertEquals(filters, McpCursor.Filters.of(TOOL, null, "text", 7, 7L, true, Mode.CPU));
        }

        /** An enum is its constant name, written by the cursor itself and not by a serialiser. */
        @Test
        void writeAnEnumAsItsName() {
            assertEquals(McpCursor.Filters.of(TOOL, "CPU"), McpCursor.Filters.of(TOOL, Mode.CPU));
        }

        /** Anything else has no single spelling the fingerprint can rely on, so it is refused. */
        @Test
        void refuseEveryOtherType() {
            for (Object value : List.of(1.5, Instant.EPOCH, List.of("a"), new Object(), 'c')) {
                assertThrows(IllegalArgumentException.class, () -> McpCursor.Filters.of(TOOL, value),
                        value.getClass().getName());
            }
        }
    }

    @Nested
    class Size {

        @Test
        void refusesACursorLongerThanTheCap() {
            String cursor = CURSOR.encode(FILTERS, AFTER);
            String longer = cursor + "A".repeat(McpCursor.MAX_CURSOR_CHARS + 1 - cursor.length());

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decode(longer, FILTERS));
            assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
        }

        /** The server never hands out a cursor it would refuse to read back. */
        @Test
        void refusesToEncodeAPositionTooLargeForTheCap() {
            McpCursor.Keyset huge = new McpCursor.Keyset(List.of("k".repeat(McpCursor.MAX_CURSOR_CHARS)));

            assertThrows(IllegalArgumentException.class, () -> CURSOR.encode(FILTERS, huge));
        }

        @Test
        void encodesAPositionThatFits() {
            McpCursor.Keyset large = new McpCursor.Keyset(List.of("k".repeat(2_000)));

            String cursor = CURSOR.encode(FILTERS, large);

            assertTrue(cursor.length() <= McpCursor.MAX_CURSOR_CHARS, String.valueOf(cursor.length()));
            assertEquals(large, CURSOR.decode(cursor, FILTERS));
        }
    }

    @Nested
    class Refusals {

        @Test
        void refusesACodecNamedByNoServer() {
            assertThrows(IllegalArgumentException.class, () -> new McpCursor(" "));
            assertThrows(IllegalArgumentException.class, () -> new McpCursor(null));
        }

        @Test
        void refusesMalformedCursors() {
            for (String cursor : Arrays.asList(null, "", " ", "not-a-cursor", "e30", "bnVsbA", "W10", "!!",
                    "x".repeat(McpCursor.MAX_CURSOR_CHARS + 1))) {
                IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                        () -> CURSOR.decode(cursor, FILTERS), String.valueOf(cursor));
                assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
            }
        }

        /**
         * Edited bytes or an edited fingerprint are refused. The fingerprint binds the filters, it
         * is not a signature: a hand-written position for the same filters reads as written, which
         * only reaches rows those filters already select.
         */
        @Test
        void refusesATamperedCursor() {
            String cursor = CURSOR.encode(FILTERS, AFTER);
            char last = cursor.charAt(cursor.length() - 1);
            String flipped = cursor.substring(0, cursor.length() - 1) + (last == 'A' ? 'B' : 'A');
            ObjectNode value = read(cursor);
            value.put("filters", value.path("filters").asString().replace('a', 'b').replace('0', '1'));
            ObjectNode wrongType = read(cursor);
            wrongType.putArray("after").add(7);

            for (String tampered : List.of(flipped, write(value), write(wrongType), cursor + "A", cursor.substring(1))) {
                IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                        () -> CURSOR.decode(tampered, FILTERS), tampered);
                assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
            }
        }

        @Test
        void refusesACursorWithAnExtraField() {
            ObjectNode value = read(CURSOR.encode(FILTERS, AFTER));
            value.put("offset", 3);

            assertThrows(IllegalArgumentException.class, () -> CURSOR.decode(write(value), FILTERS));
        }

        /** A non-canonical spelling of a valid cursor is not one this server handed out. */
        @Test
        void refusesANonCanonicalEncoding() {
            String cursor = CURSOR.encode(FILTERS, AFTER);
            String padded = Base64.getUrlEncoder().encodeToString(Base64.getUrlDecoder().decode(cursor));

            if (!padded.equals(cursor)) {
                assertThrows(IllegalArgumentException.class, () -> CURSOR.decode(padded, FILTERS));
            }
            String spaced = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (" " + new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalArgumentException.class, () -> CURSOR.decode(spaced, FILTERS));
        }

        @Test
        void refusesACursorIssuedForOtherFilters() {
            String cursor = CURSOR.encode(FILTERS, AFTER);

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decode(cursor, McpCursor.Filters.of(TOOL, "payments", null, 60)));
            assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
            assertTrue(refused.getMessage().contains("filters"), refused.getMessage());
        }

        @Test
        void refusesACursorIssuedByAnotherTool() {
            String cursor = CURSOR.encode(FILTERS, AFTER);

            assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decode(cursor, McpCursor.Filters.of("hubs_sessions", "checkout", null, 60)));
        }

        @Test
        void refusesAVersionThatIsNotAnInt() {
            for (String version : List.of("\"1\"", "1.0", "null", "4294967297")) {
                ObjectNode value = read(CURSOR.encode(FILTERS, AFTER));
                value.set("version", McpJson.readTree(version));

                IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                        () -> CURSOR.decode(write(value), FILTERS), version);
                assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
            }
        }

        @Test
        void refusesAnOffsetBeyondTheIntRange() {
            ObjectNode value = read(CURSOR.encode(FILTERS, new McpCursor.Offset(5)));
            value.put("offset", 3_000_000_000L);

            assertThrows(IllegalArgumentException.class, () -> CURSOR.decode(write(value), FILTERS));
        }

        @Test
        void refusesACursorOfAnotherVersion() {
            ObjectNode value = read(CURSOR.encode(FILTERS, AFTER));
            value.put("version", 2);

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decode(write(value), FILTERS));
            assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
            assertTrue(refused.getMessage().contains("version"), refused.getMessage());
            assertEquals("This cursor was written by another version of Example: omit cursor to start again.",
                    refused.getMessage());
        }

        @Test
        void refusesAKeysetWhereAnOffsetIsExpected() {
            String keyset = CURSOR.encode(FILTERS, AFTER);
            String offset = CURSOR.encode(FILTERS, new McpCursor.Offset(5));

            assertThrows(IllegalArgumentException.class, () -> CURSOR.decodeOffset(keyset, FILTERS));
            assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decodeKeyset(offset, FILTERS, McpCursor.Keyset::after));
        }

        /** A keyset the tool cannot read back is as malformed as one that does not parse. */
        @Test
        void aReaderThatRefusesThePositionRefusesTheCursor() {
            String cursor = CURSOR.encode(FILTERS, AFTER);

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decodeKeyset(cursor, FILTERS, keyset -> {
                        throw new IllegalStateException("not a profile id");
                    }));
            assertTrue(refused.getMessage().contains(START_AGAIN), refused.getMessage());
        }

        @Test
        void refusesANegativeOffsetAndAnEmptyKeyset() {
            assertThrows(IllegalArgumentException.class, () -> new McpCursor.Offset(-1));
            assertThrows(IllegalArgumentException.class, () -> new McpCursor.Keyset(List.of()));
            assertThrows(IllegalArgumentException.class, () -> new McpCursor.Keyset(null));
        }
    }

    @Nested
    class OffsetPaging {

        @Test
        void aPageShortOfTheTotalContinuesAfterItsLastRow() {
            McpCursor.Next next = CURSOR.nextOffset(FILTERS, 0, 50, 120);

            assertTrue(next.hasMore());
            assertEquals(50, CURSOR.decodeOffset(next.nextCursor(), FILTERS));
        }

        @Test
        void walksEveryRowExactlyOnce() {
            int total = 120;
            int seen = 0;
            String cursor = null;
            do {
                int offset = cursor == null ? 0 : CURSOR.decodeOffset(cursor, FILTERS);
                int returned = Math.min(50, total - offset);
                seen += returned;
                McpCursor.Next next = CURSOR.nextOffset(FILTERS, offset, returned, total);
                cursor = next.nextCursor();
                assertEquals(cursor != null, next.hasMore());
            } while (cursor != null);

            assertEquals(total, seen);
        }

        @Test
        void theLastPageHasNoCursor() {
            McpCursor.Next next = CURSOR.nextOffset(FILTERS, 100, 20, 120);

            assertFalse(next.hasMore());
            assertNull(next.nextCursor());
        }

        /** A live list that shrank under the cursor ends rather than pointing past its end. */
        @Test
        void anOffsetPastAShrunkenListEnds() {
            McpCursor.Next next = CURSOR.nextOffset(FILTERS, 100, 0, 40);

            assertFalse(next.hasMore());
        }

        /** Rows remain but none were returned: continuing would hand back the same cursor forever. */
        @Test
        void refusesAnEmptyPageThatClaimsToContinue() {
            assertThrows(IllegalArgumentException.class, () -> CURSOR.nextOffset(FILTERS, 10, 0, 40));
        }

        @Test
        void refusesNegativeArguments() {
            assertThrows(IllegalArgumentException.class, () -> CURSOR.nextOffset(FILTERS, -1, 5, 10));
            assertThrows(IllegalArgumentException.class, () -> CURSOR.nextOffset(FILTERS, 0, -1, 10));
            assertThrows(IllegalArgumentException.class, () -> CURSOR.nextOffset(FILTERS, 0, 5, -1));
        }

        @Test
        void aNextCursorIsBoundToItsFilters() {
            McpCursor.Next next = CURSOR.nextOffset(FILTERS, 0, 50, 120);

            assertThrows(IllegalArgumentException.class,
                    () -> CURSOR.decodeOffset(next.nextCursor(), McpCursor.Filters.of(TOOL, "payments")));
        }

        @Test
        void nextKeepsHasMoreAndTheCursorInStep() {
            assertThrows(IllegalArgumentException.class, () -> new McpCursor.Next(true, null));
            assertThrows(IllegalArgumentException.class, () -> new McpCursor.Next(false, "abc"));
        }

        @Test
        void differentOffsetsGiveDifferentCursors() {
            assertNotEquals(CURSOR.nextOffset(FILTERS, 0, 50, 120).nextCursor(),
                    CURSOR.nextOffset(FILTERS, 50, 50, 120).nextCursor());
        }
    }

    private static ObjectNode read(String cursor) {
        return (ObjectNode) McpJson.readTree(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    }

    private static String write(ObjectNode value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(McpJson.toByteArray(value));
    }
}
