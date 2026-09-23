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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedOutputTest {

    private static final String URL = "http://localhost:8585/profiles/p-1";

    @Test
    void putsTheLinkUnderTheAnswer() {
        String out = LinkedOutput.of("the answer", URL);

        assertTrue(out.startsWith("the answer"), out);
        assertTrue(out.endsWith("Open in Jeffrey: " + URL), out);
    }

    @Test
    void saysWhatTheLinkCannotReproduce() {
        String out = LinkedOutput.of("the answer", URL, "full recording");

        assertTrue(out.contains("Open in Jeffrey (full recording): " + URL), out);
    }

    @Test
    void writesTheNextStepsAsAList() {
        String out = LinkedOutput.of("the answer", List.of("first", "second"), URL);

        assertTrue(out.contains("Where to go next:"), out);
        assertTrue(out.contains("- first"), out);
        assertTrue(out.contains("- second"), out);
        assertTrue(out.indexOf("- first") < out.indexOf("Open in Jeffrey"), out);
    }

    @Test
    void leavesTheHeadingOutWhenThereIsNowhereToGo() {
        assertEquals(
                LinkedOutput.of("the answer", URL),
                LinkedOutput.of("the answer", List.of(), URL));
    }

    /**
     * The reason this exists as a type rather than as string concatenation at each call site. The cap
     * truncates at its limit, so a link appended before the cap is cut off exactly the oversized
     * answers whose reader most needs the interactive view — and the routing below it goes with it.
     */
    @Nested
    class OversizedAnswers {

        private final String oversized = "x".repeat(McpToolOutput.MAX_CHARS + 5_000);

        @Test
        void keepsTheLinkOnAnAnswerThatHadToBeCut() {
            String out = LinkedOutput.of(oversized, URL);

            assertTrue(out.contains("TRUNCATED"), "the cut is announced");
            assertTrue(out.endsWith("Open in Jeffrey: " + URL), "and the link survives it");
        }

        @Test
        void keepsTheNextStepsOnAnAnswerThatHadToBeCut() {
            String out = LinkedOutput.of(oversized, List.of("go here next"), URL);

            assertTrue(out.contains("TRUNCATED"), out.substring(out.length() - 400));
            assertTrue(out.contains("- go here next"), out.substring(out.length() - 400));
            assertTrue(out.endsWith("Open in Jeffrey: " + URL), out.substring(out.length() - 400));
        }

        @Test
        void keepsTheNoteOnAnAnswerThatHadToBeCut() {
            String out = LinkedOutput.of(oversized, List.of("go here next"), URL, "full recording");

            assertTrue(out.endsWith("Open in Jeffrey (full recording): " + URL), out.substring(
                    out.length() - 400));
        }
    }

    /**
     * A JSON answer carries its link as a field of the value instead, because appending it as text
     * would leave the result no longer parseable.
     */
    @Test
    void rendersAJsonAnswerWithoutAppendingAnything() {
        assertEquals("{\"uiLink\":\"" + URL + "\"}", LinkedOutput.json(new Linked(URL)));
    }

    private record Linked(String uiLink) {
    }
}
