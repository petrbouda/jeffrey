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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolOutputTest {

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

    /**
     * A body capped to leave room for what follows it: the cut and its note together stay within the
     * budget, so a footer appended afterwards still fits under the text cap.
     */
    @Test
    void cappedWithinABudgetKeepsTheNoteInsideIt() {
        int budget = McpToolOutput.MAX_CHARS - 500;

        String result = McpToolOutput.cappedWithin("x".repeat(McpToolOutput.MAX_CHARS), budget);

        assertEquals(budget, result.length());
        assertTrue(result.contains("TRUNCATED"), result.substring(result.length() - 200));
    }

    @Test
    void cappedWithinLeavesABodyThatFitsAlone() {
        String body = "x".repeat(1_000);

        assertEquals(body, McpToolOutput.cappedWithin(body, 1_000));
        assertEquals("", McpToolOutput.cappedWithin(null, 1_000));
    }

    @Test
    void cappedWithinRefusesABudgetTooSmallForItsOwnNote() {
        assertThrows(IllegalArgumentException.class, () -> McpToolOutput.cappedWithin("x".repeat(100), 10));
    }

    /**
     * One truncation convention: a structured answer declares its own cut in its record, a text answer
     * ends in the TRUNCATED line. A second, trimmed-tree form with a {@code _truncated} object would be
     * a third shape an agent has to know, and the server's instructions say there is none.
     */
    @Test
    void offersNoTrimmedTreeRendering() {
        List<String> renderers = Arrays.stream(McpToolOutput.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();
        assertFalse(renderers.contains("json"), renderers.toString());
    }

    @Test
    void raisesErrorsSoTheProtocolCanTellThemFromData() {
        ToolExecutionException error = assertThrows(
                ToolExecutionException.class,
                () -> McpToolOutput.error("nothing here"));

        assertEquals("nothing here", error.getMessage());
    }
}
