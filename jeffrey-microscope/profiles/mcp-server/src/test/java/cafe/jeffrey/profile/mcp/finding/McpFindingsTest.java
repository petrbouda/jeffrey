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

package cafe.jeffrey.profile.mcp.finding;

import cafe.jeffrey.profile.mcp.finding.McpFinding.Severity;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpFindingsTest {

    @Nested
    class Id {

        @Test
        void joinsCategoryAndASluggedSubject() {
            assertEquals("garbage_collection:long-gc-pauses",
                    McpFindings.id("garbage_collection", "Long GC Pauses"));
        }

        @Test
        void isCaseAndPunctuationInsensitiveSoTwoToolsCanAgreeOnIt() {
            assertEquals(
                    McpFindings.id("GC", "Long GC Pauses!"),
                    McpFindings.id("gc", "long_gc pauses"));
        }

        @Test
        void fallsBackToAGeneralSubject() {
            assertEquals("threads:general", McpFindings.id("threads", " "));
        }
    }

    @Nested
    class Building {

        @Test
        void carriesTheIdAndTheEvidenceInInsertionOrder() {
            McpFinding finding = McpFinding.of("gc", "pauses")
                    .severity(Severity.WARNING)
                    .title("Long GC pauses")
                    .evidence("totalPauseMillis", 1234)
                    .evidence("collections", 17)
                    .evidence("missing", null)
                    .source("jvm_gc")
                    .nextTool("jvm_gc")
                    .build();

            assertEquals("gc:pauses", finding.id());
            assertEquals(List.of("totalPauseMillis", "collections"), List.copyOf(finding.evidence().keySet()));
            assertEquals(Severity.WARNING, finding.severity());
        }

        @Test
        void refusesAFindingWithoutATitle() {
            assertThrows(IllegalArgumentException.class, () -> McpFinding.of("gc", "pauses").build());
        }

        @Test
        void rendersAsPlainJsonForAToolResult() {
            String json = Json.toString(McpFinding.of("container", "cpu-throttling")
                    .severity(Severity.CRITICAL)
                    .title("Throttled")
                    .evidence("peakRatioPct", 42.5)
                    .build());

            assertTrue(json.contains("\"id\":\"container:cpu-throttling\""), json);
            assertTrue(json.contains("\"severity\":\"CRITICAL\""), json);
            assertTrue(json.contains("\"evidence\":{\"peakRatioPct\":42.5}"), json);
        }
    }

    @Nested
    class Merging {

        private McpFinding finding(String category, String subject, Severity severity, String title) {
            return McpFinding.of(category, subject).severity(severity).title(title).build();
        }

        /**
         * The whole point of a stable id: two tools that noticed the same condition produce one
         * finding, and the one that sounded the alarm louder is the one that survives.
         */
        @Test
        void keepsTheMoreSevereOfTwoFindingsWithTheSameId() {
            List<McpFinding> merged = McpFindings.merge(
                    List.of(finding("gc", "pauses", Severity.INFO, "from the rules")),
                    List.of(finding("gc", "pauses", Severity.WARNING, "from the dashboard")));

            assertEquals(1, merged.size());
            assertEquals("from the dashboard", merged.getFirst().title());
        }

        @Test
        void keepsTheFirstWordingOnATie() {
            List<McpFinding> merged = McpFindings.merge(
                    List.of(finding("gc", "pauses", Severity.WARNING, "first")),
                    List.of(finding("gc", "pauses", Severity.WARNING, "second")));

            assertEquals("first", merged.getFirst().title());
        }

        @Test
        void ordersBySeverityAndTolerateNullLists() {
            List<McpFinding> merged = McpFindings.merge(
                    List.of(finding("a", "x", Severity.OK, "ok"), finding("b", "y", Severity.CRITICAL, "critical")),
                    null,
                    List.of(finding("c", "z", Severity.WARNING, "warning")));

            assertEquals(List.of("critical", "warning", "ok"), merged.stream().map(McpFinding::title).toList());
        }

        @Test
        void countsEverySeverityEvenAtZero() {
            Map<String, Integer> counts = McpFindings.countBySeverity(List.of(
                    finding("a", "x", Severity.WARNING, "w"),
                    finding("b", "y", Severity.WARNING, "w2"),
                    finding("c", "z", Severity.OK, "ok")));

            assertEquals(Map.of("CRITICAL", 0, "WARNING", 2, "INFO", 0, "OK", 1), counts);
            assertEquals(List.of("CRITICAL", "WARNING", "INFO", "OK"), List.copyOf(counts.keySet()));
        }
    }
}
