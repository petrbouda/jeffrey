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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void rendersJson() {
        assertEquals("{\"value\":1}", McpToolOutput.json(new Sample(1)));
    }

    @Test
    void marksErrorsSoTheModelCanTellThemFromData() {
        assertTrue(McpToolOutput.error("nothing here").startsWith("Error: "));
    }

    private record Sample(int value) {
    }
}
