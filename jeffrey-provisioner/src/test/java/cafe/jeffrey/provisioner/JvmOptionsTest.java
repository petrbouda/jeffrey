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

package cafe.jeffrey.provisioner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JvmOptionsTest {

    @Nested
    @DisplayName("split")
    class Split {

        @Test
        void splitsOnWhitespace() {
            assertEquals(
                    List.of("-Xmx1200m", "-XX:+UseG1GC"),
                    JvmOptions.split("-Xmx1200m -XX:+UseG1GC"));
        }

        @Test
        void collapsesRunsOfWhitespace() {
            assertEquals(
                    List.of("-Xmx1200m", "-XX:+UseG1GC"),
                    JvmOptions.split("  -Xmx1200m \t\n  -XX:+UseG1GC  "));
        }

        @Test
        void keepsDoubleQuotedWhitespaceInsideOneOption() {
            assertEquals(
                    List.of("-Djeffrey.dir=/opt/my app", "-Xmx1g"),
                    JvmOptions.split("-Djeffrey.dir=\"/opt/my app\" -Xmx1g"));
        }

        @Test
        void keepsSingleQuotedWhitespaceInsideOneOption() {
            assertEquals(
                    List.of("-Djeffrey.dir=/opt/my app"),
                    JvmOptions.split("-Djeffrey.dir='/opt/my app'"));
        }

        @Test
        void keepsAWhollyQuotedOptionIntact() {
            assertEquals(
                    List.of("-Djeffrey.dir=/opt/my app"),
                    JvmOptions.split("\"-Djeffrey.dir=/opt/my app\""));
        }

        @Test
        void keepsAnEmptyQuotedValue() {
            assertEquals(List.of("-Djeffrey.dir="), JvmOptions.split("-Djeffrey.dir=\"\""));
        }

        @Test
        void treatsTheOtherQuoteCharacterAsContent() {
            assertEquals(List.of("-Dmsg=it's fine"), JvmOptions.split("\"-Dmsg=it's fine\""));
        }

        @Test
        void returnsEmptyForNullOrBlank() {
            assertEquals(List.of(), JvmOptions.split(null));
            assertEquals(List.of(), JvmOptions.split("   "));
        }
    }

    @Nested
    @DisplayName("quoteForArgFile")
    class QuoteForArgFile {

        @Test
        void leavesAnOptionWithoutWhitespaceAlone() {
            assertEquals("-Xmx1200m", JvmOptions.quoteForArgFile("-Xmx1200m"));
        }

        @Test
        void quotesAnOptionCarryingWhitespace() {
            assertEquals(
                    "\"-Djeffrey.dir=/opt/my app\"",
                    JvmOptions.quoteForArgFile("-Djeffrey.dir=/opt/my app"));
        }

        @Test
        void escapesQuotesAndBackslashesInsideAQuotedOption() {
            assertEquals(
                    "\"-Dmsg=say \\\"hi\\\" now\"",
                    JvmOptions.quoteForArgFile("-Dmsg=say \"hi\" now"));
            assertEquals(
                    "\"-Dp=C:\\\\my dir\"",
                    JvmOptions.quoteForArgFile("-Dp=C:\\my dir"));
        }
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        /** What the JVM does when it reads the generated argfile back. */
        @Test
        void quotedOptionSurvivesSplitThenQuote() {
            String command = "-Djeffrey.dir=\"/opt/my app\" -Xmx1g";

            List<String> options = JvmOptions.split(command);
            String argFileLines = String.join("\n", options.stream().map(JvmOptions::quoteForArgFile).toList());

            assertEquals("\"-Djeffrey.dir=/opt/my app\"\n-Xmx1g", argFileLines);
            assertEquals(options, JvmOptions.split(argFileLines));
        }
    }
}
