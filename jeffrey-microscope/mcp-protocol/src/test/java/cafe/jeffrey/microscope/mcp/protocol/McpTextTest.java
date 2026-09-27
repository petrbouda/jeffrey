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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTextTest {

    private static final int LIMIT = 1_000;

    @Test
    void leavesAShortTextAlone() {
        assertEquals("hello", McpText.capped("hello", LIMIT));
    }

    @Test
    void rendersNullAsEmpty() {
        assertEquals("", McpText.capped(null, LIMIT));
        assertEquals("", McpText.cappedWithin(null, LIMIT));
    }

    /**
     * The whole point of the cap: a cut text must say it was cut, naming the limit. A model that cannot
     * see the truncation reports the visible part as the whole story.
     */
    @Test
    void announcesTruncationNamingTheLimit() {
        String result = McpText.capped("x".repeat(LIMIT + 1), LIMIT);

        assertTrue(result.startsWith("x".repeat(LIMIT)));
        assertEquals("x".repeat(LIMIT) + "\n\n_TRUNCATED: the result exceeded 1000 characters and was cut here. "
                + "Narrow the query — a smaller limit, a time range, or a more specific filter._", result);
    }

    @Test
    void doesNotAnnounceTruncationAtExactlyTheLimit() {
        String result = McpText.capped("x".repeat(LIMIT), LIMIT);

        assertEquals(LIMIT, result.length());
        assertFalse(result.contains("TRUNCATED"));
    }

    /**
     * A body capped to leave room for what follows it: the cut and its note together stay within the
     * budget, so a footer appended afterwards still fits under the text cap.
     */
    @Test
    void cappedWithinABudgetKeepsTheNoteInsideIt() {
        int budget = LIMIT - 100;

        String result = McpText.cappedWithin("x".repeat(LIMIT), budget);

        assertEquals(budget, result.length());
        assertTrue(result.contains("TRUNCATED"), result);
    }

    @Test
    void cappedWithinLeavesABodyThatFitsAlone() {
        String body = "x".repeat(LIMIT);

        assertEquals(body, McpText.cappedWithin(body, LIMIT));
    }

    @Test
    void cappedWithinRefusesABudgetTooSmallForItsOwnNote() {
        assertThrows(IllegalArgumentException.class, () -> McpText.cappedWithin("x".repeat(100), 10));
    }
}
