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
package cafe.jeffrey.microscope.core.mcp.tools;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolArgumentsTest {

    @Nested
    class Required {

        @Test
        void returnsTheValueTrimmed() {
            assertEquals("orders", ToolArguments.required("  orders  ", "group", "recovery"));
        }

        /**
         * A model recovers from a bad call only by reading what came back, so a refusal names the
         * argument and the tool that produces a good value for it.
         */
        @Test
        void namesTheArgumentAndHowToObtainOne() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> ToolArguments.required(null, "group", "Call jdbc_overview."));

            assertTrue(thrown.getMessage().contains("group is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("Call jdbc_overview."), thrown.getMessage());
        }

        @Test
        void treatsBlankAsMissing() {
            assertThrows(IllegalArgumentException.class,
                    () -> ToolArguments.required("   ", "group", "recovery"));
        }
    }

    @Nested
    class BoundedLimit {

        @Test
        void usesTheFallbackWhenNoLimitWasGiven() {
            assertEquals(50, ToolArguments.boundedLimit(null, 50, 500));
        }

        @Test
        void usesTheFallbackForANonsensicalLimit() {
            assertEquals(50, ToolArguments.boundedLimit(0, 50, 500));
            assertEquals(50, ToolArguments.boundedLimit(-3, 50, 500));
        }

        @Test
        void honoursALimitInsideTheRange() {
            assertEquals(7, ToolArguments.boundedLimit(7, 50, 500));
        }

        /**
         * A model asking for everything is asking for its own context to be spent on one answer, so
         * the ceiling is the tool's rather than the caller's.
         */
        @Test
        void capsALimitAboveTheCeiling() {
            assertEquals(500, ToolArguments.boundedLimit(100_000, 50, 500));
        }
    }

    @Nested
    class FirstOf {

        @Test
        void leavesAListThatAlreadyFitsAlone() {
            List<String> rows = List.of("a", "b");
            assertSame(rows, ToolArguments.firstOf(rows, 40));
        }

        @Test
        void keepsTheHeadOfAnOversizedList() {
            List<String> rows = List.of("a", "b", "c", "d");

            assertEquals(List.of("a", "b"), ToolArguments.firstOf(rows, 2));
        }

        @Test
        void handsBackAListThatDoesNotViewTheOriginal() {
            List<String> rows = new ArrayList<>(List.of("a", "b", "c"));

            List<String> head = ToolArguments.firstOf(rows, 2);
            rows.clear();

            assertEquals(List.of("a", "b"), head);
        }
    }
}
