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

package cafe.jeffrey.profile.mcp.finding;

import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.finding.McpFinding.Severity;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                    .nextTool(McpNextTool.call("jvm_gc").with("profileId", "p-1").why("shows the pauses"))
                    .build();

            assertEquals("gc:pauses", finding.id());
            assertEquals(Set.of("totalPauseMillis", "collections"), finding.evidence().value().propertyNames());
            assertEquals("[\"totalPauseMillis\",\"collections\"]",
                    Json.toString(List.copyOf(finding.evidence().value().propertyNames())));
            assertEquals(Severity.WARNING, finding.severity());
            assertEquals("jvm_gc", finding.nextTool().tool());
            assertEquals("p-1", finding.nextTool().arguments().value().path("profileId").asString());
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

        /** The one open object a finding carries: the figures differ per source, so the schema cannot list them. */
        @Test
        void isDescribedByTheGeneratorWithEvidenceAsAnOpenObject() {
            McpFinding finding = McpFinding.of("gc", "pauses").title("Long GC pauses")
                    .evidence("settings", Map.of("period", "10 ms")).build();

            McpSchemaConformance.assertConforms(Json.toTree(finding), McpSchemaGenerator.schemaOf(McpFinding.class));
            assertEquals("object", McpSchemaGenerator.schemaOf(McpFinding.class)
                    .path("properties").path("evidence").path("type").asString());
        }
    }

    /**
     * A finding names the call that carries its figures; where that tool's family is not served the
     * call is dropped rather than handed out, and the rest of the finding stays as it was.
     */
    @Nested
    class Reach {

        private McpFinding routedTo(String tool) {
            return McpFinding.of("gc", "pauses").severity(Severity.WARNING).title("Long GC pauses")
                    .evidence("pauses", 3)
                    .nextTool(McpNextTool.call(tool).with("profileId", "p-1").why("shows the figures"))
                    .build();
        }

        @Test
        void keepsACallToAServedTool() {
            List<McpFinding> reachable = McpFindings.reachable(List.of(routedTo("jvm_gc")), tool -> true);

            assertEquals("jvm_gc", reachable.getFirst().nextTool().tool());
        }

        @Test
        void dropsOnlyTheCallToAToolThatIsNotServed() {
            McpFinding finding = routedTo("blocking_monitors");

            McpFinding reachable = McpFindings.reachable(List.of(finding), tool -> !tool.startsWith("blocking_"))
                    .getFirst();

            assertNull(reachable.nextTool());
            assertEquals(finding.id(), reachable.id());
            assertEquals(finding.severity(), reachable.severity());
            assertEquals(finding.title(), reachable.title());
            assertEquals(finding.evidence(), reachable.evidence());
        }

        @Test
        void leavesAFindingWithoutACallAsItIs() {
            McpFinding finding = McpFinding.of("gc", "pauses").title("Long GC pauses").build();

            assertEquals(List.of(finding), McpFindings.reachable(List.of(finding), tool -> false));
        }

        @Test
        void describesTheCallAsANullableNextTool() {
            McpFinding finding = routedTo("jvm_gc");

            McpSchemaConformance.assertConforms(Json.toTree(finding), McpSchemaGenerator.schemaOf(McpFinding.class));
            assertEquals("[\"object\",\"null\"]", Json.toString(McpSchemaGenerator.schemaOf(McpFinding.class)
                    .path("properties").path("nextTool").path("type")));
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
