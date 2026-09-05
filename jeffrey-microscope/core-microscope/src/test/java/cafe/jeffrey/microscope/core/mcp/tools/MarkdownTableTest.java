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
package cafe.jeffrey.microscope.core.mcp.tools;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTableTest {

    @Test
    void rendersAHeaderRuleAndRows() {
        String out = MarkdownTable.withColumns("id", "name")
                .row("p-1", "checkout")
                .render();

        assertEquals("""
                | id | name |
                | --- | --- |
                | p-1 | checkout |
                """, out);
    }

    /**
     * The rule the three catalogue tools each used to carry a copy of, and the reason this type
     * exists: a name with a pipe in it shifts every column after it, and the ids in that row stop
     * being the ids the next tool takes.
     */
    @Nested
    class Escaping {

        @Test
        void keepsAPipeInsideItsCell() {
            String out = MarkdownTable.withColumns("name", "id")
                    .row("checkout | before", "p-1")
                    .render();

            assertTrue(out.contains("| checkout / before | p-1 |"), out);
        }

        @Test
        void keepsANewlineFromEndingTheTable() {
            String out = MarkdownTable.withColumns("name", "id")
                    .row("first\nsecond", "p-1")
                    .render();

            assertTrue(out.contains("| first second | p-1 |"), out);
        }

        @Test
        void rendersANullCellAsEmptyRatherThanAsTheWordNull() {
            String out = MarkdownTable.withColumns("name", "id")
                    .row(null, "p-1")
                    .render();

            assertTrue(out.contains("|  | p-1 |"), out);
            assertFalse(out.contains("null"), out);
        }

        @Test
        void rendersWhateverTheCallerHasWithoutAskingForAConversion() {
            String out = MarkdownTable.withColumns("recorded", "files")
                    .row(Instant.EPOCH, 3)
                    .render();

            assertTrue(out.contains("| 1970-01-01T00:00:00Z | 3 |"), out);
        }
    }

    @Test
    void refusesARowThatDoesNotMatchTheHeader() {
        MarkdownTable table = MarkdownTable.withColumns("id", "name");

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> table.row("only-one"));

        assertTrue(thrown.getMessage().contains("2 columns"), thrown.getMessage());
    }

    @Test
    void putsEachNoteOnItsOwnLineUnderTheTable() {
        String out = MarkdownTable.withColumns("id")
                .row("p-1")
                .note("first note")
                .note("second note")
                .render();

        assertTrue(out.contains("first note"), out);
        assertTrue(out.contains("second note"), out);
        assertTrue(out.indexOf("first note") < out.indexOf("second note"), out);
    }
}
