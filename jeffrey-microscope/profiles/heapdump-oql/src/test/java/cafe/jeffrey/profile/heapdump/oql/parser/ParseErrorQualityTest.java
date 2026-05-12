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
package cafe.jeffrey.profile.heapdump.oql.parser;

import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that bad OQL input produces helpful, locatable error messages —
 * not stack traces or cryptic ANTLR internals. Locked-in by tests so the
 * surface stays user-friendly as the grammar evolves.
 */
class ParseErrorQualityTest {

    private final OqlEngine engine = new OqlEngine();

    @Test
    void emptyQuery() {
        OqlParseException ex = assertThrows(OqlParseException.class, () -> engine.parse(""));
        assertTrue(ex.getMessage().contains("empty") || ex.getMessage().toLowerCase().contains("empty"),
                () -> "message should say the query is empty: " + ex.getMessage());
    }

    @Test
    void unknownAttributeNamesAreListed() {
        OqlParseException ex = assertThrows(
                OqlParseException.class,
                () -> engine.parse("SELECT s.@xyzNotAnAttribute FROM java.lang.String s"));
        assertTrue(ex.getMessage().contains("Unknown attribute"),
                () -> "wanted 'Unknown attribute' prefix: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("retainedHeapSize"),
                () -> "wanted the known list in the message: " + ex.getMessage());
    }

    @Test
    void unknownFunctionsAreNamed() {
        OqlParseException ex = assertThrows(
                OqlParseException.class,
                () -> engine.parse("SELECT madeUpFunction(s) FROM java.lang.String s"));
        assertTrue(ex.getMessage().contains("Unknown function"),
                () -> "wanted 'Unknown function' prefix: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("madeUpFunction"),
                () -> "wanted the bad name in the message: " + ex.getMessage());
    }

    @Test
    void unknownHeapHelperMentionsValidOnes() {
        OqlParseException ex = assertThrows(
                OqlParseException.class,
                () -> engine.parse("SELECT * FROM heap.bogus()"));
        assertTrue(ex.getMessage().contains("Unknown heap helper"),
                () -> "wanted 'Unknown heap helper': " + ex.getMessage());
        assertTrue(ex.getMessage().contains("heap.objects") || ex.getMessage().contains("heap.findClass"),
                () -> "wanted suggestions: " + ex.getMessage());
    }

    @Test
    void missingFromClauseReportsLocation() {
        OqlParseException ex = assertThrows(
                OqlParseException.class,
                () -> engine.parse("SELECT *"));
        assertTrue(ex.line() >= 1,
                () -> "wanted a line number, got: " + ex.location());
    }

    @Test
    void unterminatedStringReportsLocation() {
        OqlParseException ex = assertThrows(
                OqlParseException.class,
                () -> engine.parse("SELECT s FROM java.lang.String s WHERE startsWith(s, \"unterminated"));
        assertTrue(ex.line() >= 1,
                () -> "wanted a line number, got: " + ex.location());
    }

    @Test
    void starArgumentRejectedForNonAggregate() {
        OqlParseException ex = assertThrows(
                OqlParseException.class,
                () -> engine.parse("SELECT sizeof(*) FROM java.lang.String s"));
        assertTrue(ex.getMessage().contains("does not accept '*'") || ex.getMessage().contains("*"),
                () -> "wanted star-arg complaint: " + ex.getMessage());
    }
}
