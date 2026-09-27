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

import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedOutputTest {

    private static final String URL = "http://localhost:8585/profiles/p-1";

    /**
     * The footer a Markdown answer ends with, rendered from the same record as its structured content,
     * so a host that hands the model only the text still gives it the link and the next calls.
     */
    @Nested
    class Footer {

        private final McpFollowUp followUp = new McpFollowUp(
                List.of(McpNextTool.call("timeline_hotWindows").with("profileId", "p-1")
                                .with("eventType", "jdk.ExecutionSample").why("finds when"),
                        McpNextTool.call("jvm_threads").with("profileId", "p-1").why("names the threads")),
                List.of("A short piece of advice.", "x".repeat(1_000)));

        @Test
        void followsTheBodyWithTheLinkItsNoteAndTheNextCalls() {
            LinkedOutput.Footed footed = LinkedOutput.footed("the answer", followUp, URL, "full recording");

            String text = footed.text();
            assertTrue(text.startsWith("the answer"), text);
            assertTrue(text.indexOf("Open in Microscope: " + URL) > text.indexOf("the answer"), text);
            assertTrue(text.contains("Link note: full recording"), text);
            assertFalse(footed.truncated());
        }

        /** One line per next call, the tool and its arguments as compact JSON, in the record's order. */
        @Test
        void listsExactlyTheNextCallsOfTheRecord() {
            String text = LinkedOutput.footed("the answer", followUp, URL, null).text();

            assertEquals(List.of(
                            "- timeline_hotWindows {\"profileId\":\"p-1\",\"eventType\":\"jdk.ExecutionSample\"}",
                            "- jvm_threads {\"profileId\":\"p-1\"}"),
                    linesAfter(text, "Next:"));
            assertFalse(text.contains("Link note"), text);
        }

        /** A short guidance line travels in the footer; a long one stays in the record only. */
        @Test
        void carriesOnlyTheShortGuidance() {
            String text = LinkedOutput.footed("the answer", followUp, URL, null).text();

            assertTrue(text.contains("- A short piece of advice."), text);
            assertFalse(text.contains("x".repeat(1_000)), "the long line is left to the record");
        }

        @Test
        void leavesOutTheNextHeadingWhenThereIsNowhereToGo() {
            String text = LinkedOutput.footed("the answer", new McpFollowUp(List.of(), List.of()), URL, null).text();

            assertFalse(text.contains("Next:"), text);
            assertTrue(text.endsWith("Open in Microscope: " + URL), text);
        }

        /**
         * The envelope caps the whole text at the limit again, so the body is cut with the footer's
         * length reserved: a body exactly at the limit is cut, and the footer survives whole.
         */
        @Test
        void survivesABodyAtTheCap() {
            LinkedOutput.Footed footed = LinkedOutput.footed(
                    "x".repeat(McpToolOutput.MAX_CHARS), followUp, URL, "full recording");

            String text = footed.text();
            assertTrue(footed.truncated());
            assertTrue(text.length() <= McpToolOutput.MAX_CHARS, "length " + text.length());
            assertEquals(text, McpToolOutput.capped(text), "the envelope's own cap leaves it as it is");
            assertTrue(text.contains("TRUNCATED"), "the cut is announced");
            assertEquals(2, linesAfter(text, "Next:").size(), text.substring(text.length() - 600));
        }

        @Test
        void footerRendersAloneForAnAnswerWhoseBodyIsNeverCut() {
            String footer = LinkedOutput.footer(followUp, URL, null);

            assertTrue(LinkedOutput.footed("the answer", followUp, URL, null).text().endsWith(footer));
        }

        /** A tool whose subject has no page still ends with its next calls, and names no link. */
        @Test
        void aFooterWithoutAPageCarriesTheNextCallsAlone() {
            String text = LinkedOutput.footed("the answer", followUp, null, null).text();

            assertFalse(text.contains("Open in Microscope"), text);
            assertEquals(2, linesAfter(text, "Next:").size(), text);
            assertTrue(text.startsWith("the answer\n\n---\nNext:"), text);
        }

        /** No page and nowhere to go: nothing is appended at all. */
        @Test
        void aFooterWithNothingToSayIsEmpty() {
            assertEquals("", LinkedOutput.footer(new McpFollowUp(List.of(), List.of()), null, null));
        }

        /** The text still in use must not name the retired link line. */
        @Test
        void noAnswerIsFootedWithTheRetiredLinkLine() {
            assertFalse(LinkedOutput.footed("the answer", followUp, URL, "note").text().contains("Open in Jeffrey"));
        }

        private List<String> linesAfter(String text, String heading) {
            List<String> lines = List.of(text.substring(text.indexOf(heading) + heading.length()).strip().split("\n"));
            return lines.stream().takeWhile(line -> line.startsWith("- ") && line.contains(" {")).toList();
        }
    }
}
