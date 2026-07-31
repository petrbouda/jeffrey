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

package cafe.jeffrey.profile.advisor.run;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.advisor.run.AdvisorOutputParser.ParsedOutput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvisorOutputParserTest {

    @Nested
    class Sections {

        @Test
        void splitsRecommendationsAndPatch() {
            String raw = """
                    ===RECOMMENDATIONS===
                    ## Summary
                    The hot path is in Order.recompute().

                    ===PATCH===
                    diff --git a/Order.java b/Order.java
                    --- a/Order.java
                    +++ b/Order.java
                    @@ -1 +1 @@
                    -slow
                    +fast
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertTrue(output.recommendations().contains("## Summary"));
            assertFalse(output.recommendations().contains("diff --git"), "diff must not leak into recommendations");
            assertTrue(output.patch().startsWith("diff --git a/Order.java"));
            assertTrue(output.patch().contains("+fast"));
        }

        @Test
        void treatsNoPatchSentinelAsNoPatch() {
            String raw = """
                    ===RECOMMENDATIONS===
                    Nothing concrete to change.
                    ===PATCH===
                    (no patch)
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertEquals("Nothing concrete to change.", output.recommendations());
            assertNull(output.patch());
        }

        @Test
        void stripsCodeFenceAroundPatch() {
            String raw = """
                    ===RECOMMENDATIONS===
                    Some advice.
                    ===PATCH===
                    ```diff
                    diff --git a/A.java b/A.java
                    +x
                    ```
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertTrue(output.patch().startsWith("diff --git a/A.java"), output.patch());
            assertFalse(output.patch().contains("```"), "code fence must be stripped");
        }

        @Test
        void missingPatchMarkerKeepsEverythingAsRecommendations() {
            String raw = "===RECOMMENDATIONS===\nJust prose, no patch section.";

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertEquals("Just prose, no patch section.", output.recommendations());
            assertNull(output.patch());
        }

        @Test
        void handlesBlankResponse() {
            ParsedOutput output = AdvisorOutputParser.parse("   ");

            assertEquals("", output.recommendations());
            assertNull(output.patch());
            assertTrue(output.claims().isEmpty());
        }
    }

    @Nested
    class Claims {

        @Test
        void parsesFrameSourceAndTitle() {
            String raw = """
                    ===CLAIMS===
                    com/acme/RateTable.lookup | src/main/java/com/acme/RateTable.java:88 | Per-line lookup
                    java/math/BigDecimal.valueOf | src/main/java/com/acme/Rates.java | Boxing on every read
                    ===RECOMMENDATIONS===
                    Advice.
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertEquals(2, output.claims().size());
            assertEquals("com/acme/RateTable.lookup", output.claims().getFirst().citedFrame());
            assertEquals("src/main/java/com/acme/RateTable.java:88", output.claims().getFirst().sourcePath());
            assertEquals("Per-line lookup", output.claims().getFirst().title());
        }

        @Test
        void keepsClaimsOutOfTheRecommendationsMarkdown() {
            String raw = """
                    ===CLAIMS===
                    Foo.bar | Foo.java | Something
                    ===RECOMMENDATIONS===
                    ## Summary
                    Real prose.
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertFalse(output.recommendations().contains("Foo.bar |"), "claims must not leak into the report");
            assertTrue(output.recommendations().contains("Real prose."));
        }

        @Test
        void skipsMalformedLinesWithoutLosingTheRest() {
            String raw = """
                    ===CLAIMS===
                    this line has no separator at all
                    | missing frame | Title
                    Good.frame | Good.java | Good title
                    ===RECOMMENDATIONS===
                    Advice.
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertEquals(1, output.claims().size());
            assertEquals("Good.frame", output.claims().getFirst().citedFrame());
        }

        @Test
        void toleratesAMissingSourcePath() {
            String raw = """
                    ===CLAIMS===
                    Only.frame |  | Just a frame
                    ===RECOMMENDATIONS===
                    Advice.
                    """;

            ParsedOutput output = AdvisorOutputParser.parse(raw);

            assertEquals(1, output.claims().size());
            assertNull(output.claims().getFirst().sourcePath());
        }

        @Test
        void returnsNoClaimsWhenTheSectionIsAbsent() {
            ParsedOutput output = AdvisorOutputParser.parse("===RECOMMENDATIONS===\nAdvice.");

            assertTrue(output.claims().isEmpty());
        }
    }
}
