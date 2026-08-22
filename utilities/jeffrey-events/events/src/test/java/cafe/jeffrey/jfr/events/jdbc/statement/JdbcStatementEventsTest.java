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

package cafe.jeffrey.jfr.events.jdbc.statement;

import cafe.jeffrey.jfr.events.trace.SpanKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JdbcStatementEventsTest {

    private static final String NAME = "UserMapper.selectById";
    private static final String GROUP = "UserMapper";

    @Nested
    @DisplayName("Picking by verb")
    class ByVerb {

        @Test
        @DisplayName("each data verb maps to its own event class, case-insensitively")
        void dataVerbsMapToTheirClasses() {
            assertInstanceOf(JdbcQueryEvent.class, JdbcStatementEvents.forVerb("SELECT", NAME, GROUP));
            assertInstanceOf(JdbcQueryEvent.class, JdbcStatementEvents.forVerb("select", NAME, GROUP));
            assertInstanceOf(JdbcInsertEvent.class, JdbcStatementEvents.forVerb("INSERT", NAME, GROUP));
            assertInstanceOf(JdbcUpdateEvent.class, JdbcStatementEvents.forVerb("Update", NAME, GROUP));
            assertInstanceOf(JdbcDeleteEvent.class, JdbcStatementEvents.forVerb("DELETE", NAME, GROUP));
        }

        @Test
        @DisplayName("anything else lands on the execute catch-all")
        void unknownVerbsAreExecute() {
            assertInstanceOf(JdbcExecuteEvent.class, JdbcStatementEvents.forVerb("CREATE", NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class, JdbcStatementEvents.forVerb("MERGE", NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class, JdbcStatementEvents.forVerb("FLUSH", NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class, JdbcStatementEvents.forVerb(null, NAME, GROUP));
        }

        @Test
        @DisplayName("the event carries the label, group and client kind like a hand-picked one")
        void eventIsFullyConstructed() {
            JdbcBaseEvent event = JdbcStatementEvents.forVerb("SELECT", NAME, GROUP);

            assertEquals(NAME, event.name);
            assertEquals(GROUP, event.group);
            assertEquals(SpanKind.CLIENT.name(), event.kind);
        }
    }

    @Nested
    @DisplayName("Picking by SQL text")
    class BySql {

        @Test
        @DisplayName("the first keyword decides, whatever surrounds it")
        void firstKeywordDecides() {
            assertInstanceOf(JdbcQueryEvent.class,
                    JdbcStatementEvents.forSql("SELECT * FROM users", NAME, GROUP));
            assertInstanceOf(JdbcInsertEvent.class,
                    JdbcStatementEvents.forSql("  \n\tinsert into users values (?)", NAME, GROUP));
            assertInstanceOf(JdbcUpdateEvent.class,
                    JdbcStatementEvents.forSql("(UPDATE users SET name = ?)", NAME, GROUP));
        }

        @Test
        @DisplayName("a common-table expression counts as a query")
        void withCountsAsAQuery() {
            assertInstanceOf(JdbcQueryEvent.class, JdbcStatementEvents.forSql(
                    "WITH active AS (SELECT id FROM users) SELECT count(*) FROM active", NAME, GROUP));
        }

        @Test
        @DisplayName("leading comments are read past, not mistaken for the verb")
        void commentsAreSkipped() {
            assertInstanceOf(JdbcDeleteEvent.class, JdbcStatementEvents.forSql(
                    "-- cleanup expired rows\nDELETE FROM sessions WHERE expires_at < ?", NAME, GROUP));
            assertInstanceOf(JdbcQueryEvent.class, JdbcStatementEvents.forSql(
                    "/* hint: index scan */ SELECT id FROM users", NAME, GROUP));
        }

        @Test
        @DisplayName("DDL, vendor commands, and unreadable text land on the execute catch-all")
        void everythingElseIsExecute() {
            assertInstanceOf(JdbcExecuteEvent.class,
                    JdbcStatementEvents.forSql("CREATE TABLE users (id BIGINT)", NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class,
                    JdbcStatementEvents.forSql("FORCE CHECKPOINT;", NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class, JdbcStatementEvents.forSql("", NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class, JdbcStatementEvents.forSql(null, NAME, GROUP));
            assertInstanceOf(JdbcExecuteEvent.class,
                    JdbcStatementEvents.forSql("/* never closed", NAME, GROUP));
        }
    }
}
