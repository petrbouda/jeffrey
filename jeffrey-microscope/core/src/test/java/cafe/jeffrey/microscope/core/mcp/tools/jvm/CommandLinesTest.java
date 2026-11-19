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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLinesTest {

    private static final String JAVA = "/usr/lib/jvm/java-25/bin/java -cp ";
    private static final String MAIN = " com.acme.checkout.Checkout --port 8080";

    @Nested
    class Cut {

        @Test
        void aLineThatFitsIsKeptWhole() {
            String line = JAVA + "app.jar" + MAIN;

            assertSame(line, CommandLines.cut(line));
        }

        @Test
        void aLongClassPathIsDroppedFromTheMiddle() {
            String line = JAVA + "lib/a.jar:".repeat(5_000) + MAIN;

            String cut = CommandLines.cut(line);

            assertEquals(CommandLines.MAX_CHARS, cut.length());
            assertTrue(cut.startsWith(JAVA), cut);
            assertTrue(cut.endsWith(MAIN), cut);
            assertTrue(cut.contains(CommandLines.ELLIPSIS), cut);
        }

        @Test
        void aLineOneCharacterOverIsCutToTheLimit() {
            String line = "x".repeat(CommandLines.MAX_CHARS + 1);

            assertEquals(CommandLines.MAX_CHARS, CommandLines.cut(line).length());
        }

        @Test
        void noLineStaysNull() {
            assertNull(CommandLines.cut(null));
        }
    }

    @Nested
    class Chars {

        @Test
        void countsTheWholeLineBeforeTheCut() {
            String line = "y".repeat(37_893);

            assertEquals(37_893, CommandLines.chars(line));
        }

        @Test
        void isNullWithoutALine() {
            assertNull(CommandLines.chars(null));
        }
    }
}
