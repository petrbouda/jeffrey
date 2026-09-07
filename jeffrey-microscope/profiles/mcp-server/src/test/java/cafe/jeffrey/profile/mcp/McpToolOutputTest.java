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

package cafe.jeffrey.profile.mcp;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.Json;
import java.util.List;
import java.util.ArrayList;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolOutputTest {

    private record Dashboard(String title, List<String> rows) {
    }


    @Test
    void leavesAShortResultAlone() {
        assertEquals("hello", McpToolOutput.capped("hello"));
    }

    @Test
    void rendersNullAsEmpty() {
        assertEquals("", McpToolOutput.capped(null));
    }

    /**
     * The whole point of the cap: a cut result must say it was cut. A model that cannot see the
     * truncation reports the visible part as the whole story.
     */
    @Test
    void announcesTruncation() {
        String result = McpToolOutput.capped("x".repeat(McpToolOutput.MAX_CHARS + 1));

        assertTrue(result.startsWith("x".repeat(McpToolOutput.MAX_CHARS)));
        assertTrue(result.contains("TRUNCATED"));
    }

    @Test
    void doesNotAnnounceTruncationAtExactlyTheCap() {
        String result = McpToolOutput.capped("x".repeat(McpToolOutput.MAX_CHARS));

        assertEquals(McpToolOutput.MAX_CHARS, result.length());
        assertFalse(result.contains("TRUNCATED"));
    }

    @Test
    void rendersJson() {
        assertEquals("{\"value\":1}", McpToolOutput.json(new Sample(1)));
    }

    /**
     * The point of trimming in the tree rather than cutting the string: an oversized answer is still an
     * answer a client can parse.
     */
    @Test
    void anOversizedJsonResultStaysParseable() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            rows.add("row-" + i + "-with-enough-text-to-push-the-document-past-the-cap");
        }

        String rendered = McpToolOutput.json(new Dashboard("cpu", rows));

        assertTrue(rendered.length() <= McpToolOutput.MAX_CHARS);
        JsonNode parsed = Json.readTree(rendered);
        assertEquals("cpu", parsed.get("title").asString());
        assertTrue(parsed.get("rows").size() < rows.size());
    }

    @Test
    void anOversizedJsonResultSaysWhatItDropped() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            rows.add("row-" + i + "-with-enough-text-to-push-the-document-past-the-cap");
        }

        JsonNode parsed = Json.readTree(McpToolOutput.json(new Dashboard("cpu", rows)));

        JsonNode truncated = parsed.get("_truncated");
        assertNotNull(truncated, "a trimmed result must say that it was trimmed");
        JsonNode first = truncated.properties().iterator().next().getValue();
        assertEquals(20_000, first.get("original").asInt());
        assertTrue(first.get("kept").asInt() < 20_000);
    }

    /**
     * Which list was cut is the part a reader needs. An answer carrying several of them, reporting
     * only that "an array" lost rows, leaves them no better off than the field's own length did.
     */
    @Test
    void namesTheFieldWhoseListWasTrimmed() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            rows.add("row-" + i + "-with-enough-text-to-push-the-document-past-the-cap");
        }

        JsonNode parsed = Json.readTree(McpToolOutput.json(new Dashboard("cpu", rows)));

        assertTrue(parsed.get("_truncated").has("rows"),
                "the trimmed list is reported under the field holding it");
    }

    @Test
    void aJsonResultThatFitsIsLeftExactlyAsItWas() {
        String rendered = McpToolOutput.json(new Dashboard("cpu", List.of("a", "b")));
        assertEquals("{\"title\":\"cpu\",\"rows\":[\"a\",\"b\"]}", rendered);
    }

    /**
     * The record of what was lost is part of the answer, so it has to be inside the cap rather than
     * appended past it. Attaching it after the trim loop had already stopped at the ceiling pushed the
     * rendering back over, and the cut that followed left JSON ending mid-token with a Markdown
     * sentence stuck to it — the one outcome this method exists to prevent.
     */
    @Test
    void keepsAnOversizedJsonResultParseableOnceTheTruncationRecordIsAttached() {
        List<String> rows = new ArrayList<>();
        // Sized so the trimmed rendering lands just under the cap, leaving no room for the record.
        for (int i = 0; i < 4_000; i++) {
            rows.add("row-" + i + "-" + "y".repeat(55));
        }

        String json = McpToolOutput.json(new Dashboard("t", rows));

        assertTrue(json.length() <= McpToolOutput.MAX_CHARS, "length=" + json.length());
        assertFalse(json.contains("TRUNCATED:"), "a JSON answer must not carry the Markdown note");
        JsonNode parsed = Json.readTree(json);
        assertNotNull(parsed.get("_truncated"));
    }

    /**
     * A bare list is the case the record had nowhere to hang: it was trimmed and handed back short with
     * nothing saying so, which reads exactly like a complete list of that length.
     */
    @Test
    void saysSoWhenATopLevelListHadToLoseRows() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            rows.add("row-" + i + "-" + "z".repeat(40));
        }

        String json = McpToolOutput.json(rows);

        JsonNode parsed = Json.readTree(json);
        assertTrue(parsed.isObject(), "a trimmed list is wrapped so the record has somewhere to hang");
        assertTrue(parsed.get("items").size() < rows.size());
        assertNotNull(parsed.get("_truncated"));
        assertTrue(json.length() <= McpToolOutput.MAX_CHARS);
    }

    @Test
    void leavesAListThatFitsAsAList() {
        String json = McpToolOutput.json(List.of("a", "b"));

        assertEquals("[\"a\",\"b\"]", json);
    }

    @Test
    void marksErrorsSoTheModelCanTellThemFromData() {
        assertTrue(McpToolOutput.error("nothing here").startsWith("Error: "));
    }

    private record Sample(int value) {
    }
}
