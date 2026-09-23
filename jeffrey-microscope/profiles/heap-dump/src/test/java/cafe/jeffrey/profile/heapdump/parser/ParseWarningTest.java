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
