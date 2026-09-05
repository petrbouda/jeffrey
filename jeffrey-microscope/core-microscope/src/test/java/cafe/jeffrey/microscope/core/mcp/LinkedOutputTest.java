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
