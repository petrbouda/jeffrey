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
package cafe.jeffrey.profile.heapdump.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseWarningTest {

    @Nested
    class ConstructorInvariants {

        @Test
        void nullSeverityRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ParseWarning(0L, null, null, "msg"));
        }

        @Test
        void nullMessageRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ParseWarning(0L, null, ParseWarning.Severity.WARN, null));
        }

        @Test
        void emptyMessageRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ParseWarning(0L, null, ParseWarning.Severity.WARN, ""));
        }

        @Test
        void nullRecordKindAccepted() {
            // Per Javadoc, recordKind is nullable for warnings that aren't specific
            // to a record (e.g. truncated header).
            ParseWarning w = new ParseWarning(42L, null, ParseWarning.Severity.INFO, "truncated header");
            assertNull(w.recordKind());
            assertEquals(42L, w.fileOffset());
            assertEquals(ParseWarning.Severity.INFO, w.severity());
            assertEquals("truncated header", w.message());
        }
    }

    @Nested
    class AnyError {

        @Test
        void emptyListIsFalse() {
            assertFalse(ParseWarning.anyError(List.of()));
        }

        @Test
        void infoAndWarnOnlyIsFalse() {
            List<ParseWarning> ws = List.of(
                    new ParseWarning(0L, null, ParseWarning.Severity.INFO, "info"),
                    new ParseWarning(1L, null, ParseWarning.Severity.WARN, "warn"));
            assertFalse(ParseWarning.anyError(ws));
        }

        @Test
        void singleErrorIsTrue() {
            List<ParseWarning> ws = List.of(
                    new ParseWarning(0L, null, ParseWarning.Severity.ERROR, "broken"));
            assertTrue(ParseWarning.anyError(ws));
        }

        @Test
        void errorMixedWithOthersIsTrue() {
            List<ParseWarning> ws = List.of(
                    new ParseWarning(0L, null, ParseWarning.Severity.INFO, "info"),
                    new ParseWarning(1L, null, ParseWarning.Severity.ERROR, "fatal"),
                    new ParseWarning(2L, null, ParseWarning.Severity.WARN, "warn"));
            assertTrue(ParseWarning.anyError(ws));
        }
    }
}
