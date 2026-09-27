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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The W3C trace context a client may send in {@code params._meta}: read exactly as the specification
 * spells it, and dropped rather than repaired when it does not.
 */
class McpTraceContextTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final String TRACESTATE = "rojo=00f067aa0ba902b7,congo=t61rcWkgMzE";

    private static Optional<McpTraceContext> read(String meta) {
        return McpTraceContext.from(McpJson.readTree(meta));
    }

    private static Optional<McpTraceContext> readTraceparent(String traceparent) {
        ObjectNode meta = McpJson.createObject().put("traceparent", traceparent);
        return McpTraceContext.from(meta);
    }

    @Nested
    class WellFormed {

        @Test
        void keepsATraceparentAndItsTracestateVerbatim() {
            McpTraceContext trace = read("""
                    {"traceparent":"%s","tracestate":"%s"}""".formatted(TRACEPARENT, TRACESTATE)).orElseThrow();

            assertEquals(TRACEPARENT, trace.traceparent());
            assertEquals(TRACESTATE, trace.tracestate());
        }

        @Test
        void keepsATraceparentWithoutATracestate() {
            McpTraceContext trace = readTraceparent(TRACEPARENT).orElseThrow();

            assertEquals(TRACEPARENT, trace.traceparent());
            assertNull(trace.tracestate());
        }

        /** Any flags byte is carried: the sampled bit is the client's business, not the server's. */
        @Test
        void keepsAnyFlags() {
            assertTrue(readTraceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00").isPresent());
            assertTrue(readTraceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-ff").isPresent());
        }

        @Test
        void keepsATracestateAtTheCap() {
            String longest = "k=" + "v".repeat(McpTraceContext.MAX_TRACESTATE_CHARS - 2);

            McpTraceContext trace = read("""
                    {"traceparent":"%s","tracestate":"%s"}""".formatted(TRACEPARENT, longest)).orElseThrow();

            assertEquals(longest, trace.tracestate());
        }
    }

    @Nested
    class Malformed {

        @ParameterizedTest
        @ValueSource(strings = {
                // Upper-case hex: the specification allows lower case only.
                "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00F067AA0BA902B7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-0A",
                // All-zero ids are invalid by definition.
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
                // Only version 00 is understood; ff is forbidden outright.
                "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                // Too long: a trailing field, a longer id, a trailing space.
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra",
                "00-4bf92f3577b34da6a3ce929d0e0e47360-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01 ",
                // Too short, wrong separators, not hex, empty.
                "00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01",
                "00_4bf92f3577b34da6a3ce929d0e0e4736_00f067aa0ba902b7_01",
                "00-4bf92f3577b34da6a3ce929d0e0e473g-00f067aa0ba902b7-01",
                "",
                " "})
        void dropsATraceparentOutsideTheFormat(String traceparent) {
            assertTrue(readTraceparent(traceparent).isEmpty(), traceparent);
        }

        @Test
        void dropsATraceparentThatIsNotAString() {
            assertTrue(read("{\"traceparent\":42}").isEmpty());
            assertTrue(read("{\"traceparent\":{\"id\":\"x\"}}").isEmpty());
            assertTrue(read("{\"traceparent\":null}").isEmpty());
        }

        /** A tracestate means nothing without the traceparent it continues, so it goes with it. */
        @Test
        void dropsTheTracestateWithAMalformedTraceparent() {
            assertTrue(read("""
                    {"traceparent":"00-bad","tracestate":"%s"}""".formatted(TRACESTATE)).isEmpty());
        }

        @Test
        void keepsTheTraceparentButDropsATracestateOverTheCap() {
            String tooLong = "k=" + "v".repeat(McpTraceContext.MAX_TRACESTATE_CHARS - 1);

            McpTraceContext trace = read("""
                    {"traceparent":"%s","tracestate":"%s"}""".formatted(TRACEPARENT, tooLong)).orElseThrow();

            assertEquals(TRACEPARENT, trace.traceparent());
            assertNull(trace.tracestate());
        }

        @Test
        void keepsTheTraceparentButDropsATracestateThatIsNotAString() {
            McpTraceContext trace = read("""
                    {"traceparent":"%s","tracestate":7}""".formatted(TRACEPARENT)).orElseThrow();

            assertNull(trace.tracestate());
        }

        @Test
        void keepsTheTraceparentButDropsABlankTracestate() {
            McpTraceContext trace = read("""
                    {"traceparent":"%s","tracestate":" "}""".formatted(TRACEPARENT)).orElseThrow();

            assertNull(trace.tracestate());
        }
    }

    @Nested
    class Absent {

        @Test
        void isEmptyWithoutATraceparent() {
            assertTrue(read("{}").isEmpty());
            assertTrue(read("{\"tracestate\":\"%s\"}".formatted(TRACESTATE)).isEmpty());
        }

        @Test
        void isEmptyWithoutMeta() {
            assertTrue(McpTraceContext.from(null).isEmpty());
        }

        @Test
        void isEmptyWhenMetaIsNotAnObject() {
            JsonNode notAnObject = McpJson.readTree("[\"%s\"]".formatted(TRACEPARENT));

            assertTrue(McpTraceContext.from(notAnObject).isEmpty());
        }
    }

    /** The record itself holds only what the reader would have kept. */
    @Nested
    class Construction {

        @Test
        void refusesAMalformedTraceparent() {
            assertThrows(IllegalArgumentException.class, () -> new McpTraceContext("00-bad", null));
            assertThrows(IllegalArgumentException.class, () -> new McpTraceContext(null, null));
        }

        @Test
        void refusesATracestateOverTheCap() {
            String tooLong = "v".repeat(McpTraceContext.MAX_TRACESTATE_CHARS + 1);

            assertThrows(IllegalArgumentException.class, () -> new McpTraceContext(TRACEPARENT, tooLong));
        }

        @Test
        void refusesABlankTracestate() {
            assertThrows(IllegalArgumentException.class, () -> new McpTraceContext(TRACEPARENT, ""));
        }

        @Test
        void acceptsAWellFormedPair() {
            McpTraceContext trace = new McpTraceContext(TRACEPARENT, TRACESTATE);

            assertEquals(TRACEPARENT, trace.traceparent());
            assertFalse(trace.tracestate().isBlank());
        }
    }
}
