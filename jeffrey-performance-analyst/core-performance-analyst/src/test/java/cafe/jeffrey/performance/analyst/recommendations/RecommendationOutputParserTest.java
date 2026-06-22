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

package cafe.jeffrey.performance.analyst.recommendations;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationOutputParserTest {

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

        RecommendationResult result = RecommendationOutputParser.parse(raw);

        assertTrue(result.recommendations().contains("## Summary"));
        assertFalse(result.recommendations().contains("diff --git"), "diff must not leak into recommendations");
        assertTrue(result.hasPatch());
        assertTrue(result.patch().startsWith("diff --git a/Order.java"));
        assertTrue(result.patch().contains("+fast"));
    }

    @Test
    void parsesSeverityFromItsSection() {
        String raw = """
                ===SEVERITY===
                CRITICAL
                ===RECOMMENDATIONS===
                ## Summary
                Hot path dominates.
                ===PATCH===
                (no patch)
                """;

        RecommendationResult result = RecommendationOutputParser.parse(raw);

        assertEquals(Severity.CRITICAL, result.severity());
        assertTrue(result.recommendations().contains("Hot path dominates."));
        assertFalse(result.recommendations().contains("CRITICAL"), "severity must not leak into recommendations");
    }

    @Test
    void severityToleratesExtraWordsAndCase() {
        String raw = """
                ===SEVERITY===
                high — about 14% of CPU
                ===RECOMMENDATIONS===
                Advice.
                """;

        assertEquals(Severity.HIGH, RecommendationOutputParser.parse(raw).severity());
    }

    @Test
    void severityDefaultsToMediumWhenMissing() {
        String raw = "===RECOMMENDATIONS===\nNo severity section here.";

        assertEquals(Severity.MEDIUM, RecommendationOutputParser.parse(raw).severity());
    }

    @Test
    void severityDefaultsToMediumWhenUnknown() {
        String raw = "===SEVERITY===\nBANANA\n===RECOMMENDATIONS===\nAdvice.";

        assertEquals(Severity.MEDIUM, RecommendationOutputParser.parse(raw).severity());
    }

    @Test
    void treatsNoPatchSentinelAsNoPatch() {
        String raw = """
                ===RECOMMENDATIONS===
                Nothing concrete to change.
                ===PATCH===
                (no patch)
                """;

        RecommendationResult result = RecommendationOutputParser.parse(raw);

        assertEquals("Nothing concrete to change.", result.recommendations());
        assertFalse(result.hasPatch());
        assertNull(result.patch());
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

        RecommendationResult result = RecommendationOutputParser.parse(raw);

        assertTrue(result.hasPatch());
        assertTrue(result.patch().startsWith("diff --git a/A.java"), result.patch());
        assertFalse(result.patch().contains("```"), "code fence must be stripped");
    }

    @Test
    void missingPatchMarkerKeepsEverythingAsRecommendations() {
        String raw = "===RECOMMENDATIONS===\nJust prose, no patch section.";

        RecommendationResult result = RecommendationOutputParser.parse(raw);

        assertEquals("Just prose, no patch section.", result.recommendations());
        assertFalse(result.hasPatch());
    }

    @Test
    void handlesBlankInput() {
        RecommendationResult result = RecommendationOutputParser.parse("  ");

        assertEquals("", result.recommendations());
        assertFalse(result.hasPatch());
    }
}
