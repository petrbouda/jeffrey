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

import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the footer a Markdown answer ends with against the record it was rendered from: the link, its
 * note when there is one, and one {@code Next:} line per next call, in order, after the body.
 */
public final class MarkdownFooters {

    private static final String LINK_PREFIX = "Open in Microscope: ";
    private static final String NOTE_PREFIX = "Link note: ";
    private static final String NEXT_HEADING = "Next:";
    private static final String CALL_PREFIX = "- ";
    private static final String RULE = "\n\n---\n";

    private MarkdownFooters() {
    }

    /**
     * @param text       the answer's text
     * @param structured the answer's structured content, carrying {@code uiLink}, {@code followUp} and,
     *                   optionally, {@code uiLinkNote}; a tool with no page carries no {@code uiLink}
     */
    public static void assertRenderedFrom(String text, JsonNode structured) {
        JsonNode uiLink = structured.path("uiLink");
        int link;
        if (uiLink.isString()) {
            link = text.lastIndexOf(LINK_PREFIX + uiLink.asString());
            assertTrue(link > 0, "no footer link after a body: " + tail(text));
        } else {
            // A tool with no page: the footer is the rule and the next calls, with no link line.
            assertTrue(!text.contains(LINK_PREFIX), "a tool with no page names no link: " + tail(text));
            link = text.lastIndexOf(RULE);
            assertTrue(link > 0 || structured.get("followUp").get("nextTools").isEmpty(),
                    "no footer after a body: " + tail(text));
            link = Math.max(link, 0);
        }
        JsonNode note = structured.path("uiLinkNote");
        if (note.isString()) {
            assertTrue(text.indexOf(NOTE_PREFIX + note.asString(), link) > link, tail(text));
        }

        List<String> expected = new ArrayList<>();
        for (JsonNode call : structured.get("followUp").get("nextTools")) {
            expected.add(CALL_PREFIX + call.get("tool").asString() + " " + Json.toString(call.get("arguments"))
                    + " [" + call.get("weight").asString() + "]");
        }
        assertEquals(expected, nextLines(text.substring(link)), tail(text));
    }

    private static List<String> nextLines(String footer) {
        int heading = footer.indexOf(NEXT_HEADING);
        if (heading < 0) {
            return List.of();
        }
        return Arrays.stream(footer.substring(heading + NEXT_HEADING.length()).strip().split("\n"))
                .takeWhile(line -> line.startsWith(CALL_PREFIX) && line.contains(" {"))
                .toList();
    }

    private static String tail(String text) {
        return text.substring(Math.max(0, text.length() - 1_500));
    }
}
