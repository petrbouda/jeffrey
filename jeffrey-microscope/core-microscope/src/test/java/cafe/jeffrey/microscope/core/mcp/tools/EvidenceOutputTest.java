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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.tools.EvidenceOutput.Truncation;
import cafe.jeffrey.microscope.core.mcp.tools.EvidenceOutput.TruncationReason;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound on an evidence document, which decides what a caller actually receives when a profile
 * holds more than fits. The figures are the point: a collection says how much it dropped and why.
 */
class EvidenceOutputTest {

    private static final int ROW_LIMIT = 100;

    /** A document with two bounded collections, shaped like the evidence records. */
    record Document(int schemaVersion, Map<String, Truncation> truncation, List<String> events, List<String> threads) {
    }

    private static final Document SKELETON = new Document(1, Map.of(), List.of(), List.of());

    /** Rows wide enough that a few hundred of them pass the output budget. */
    private static List<String> wideRows(int count, int width) {
        return IntStream.range(0, count).mapToObj(index -> index + "-" + "x".repeat(width)).toList();
    }

    @Nested
    class Bounding {

        @Test
        void keepsEverythingWhenItFits() {
            EvidenceOutput output = new EvidenceOutput(SKELETON, ROW_LIMIT);
            List<String> kept = output.rows("events", wideRows(10, 8));

            assertEquals(10, kept.size());
            assertEquals(new Truncation(10, 10, 0, TruncationReason.COMPLETE), output.truncation().get("events"));
        }

        @Test
        void stopsAtTheRowLimitAndSaysSo() {
            EvidenceOutput output = new EvidenceOutput(SKELETON, ROW_LIMIT);
            List<String> kept = output.rows("events", wideRows(ROW_LIMIT + 40, 8));

            assertEquals(ROW_LIMIT, kept.size());
            assertEquals(new Truncation(ROW_LIMIT + 40, ROW_LIMIT, 40, TruncationReason.ROW_LIMIT),
                    output.truncation().get("events"));
        }

        /**
         * The running figure replaced a re-serialisation of the whole document per row. It is an
         * upper bound, so it may stop a row early, but the document it produces has to stay inside
         * the budget the tool promises.
         */
        @Test
        void stopsOnTheOutputBudgetBeforeTheRowLimit() {
            int rowWidth = 4_000;
            int rows = McpToolOutput.MAX_CHARS / rowWidth + 20;
            EvidenceOutput output = new EvidenceOutput(SKELETON, rows);
            List<String> events = output.rows("events", wideRows(rows, rowWidth));

            Truncation counts = output.truncation().get("events");
            McpToolResult result = McpToolResult.of(new Document(1, output.truncation(), events, List.of()));
            assertEquals(rows, counts.total());
            assertTrue(counts.returned() > 0, "a budget that fits several rows returns some");
            assertTrue(counts.omitted() > 0, "and drops the ones past it");
            assertEquals(TruncationReason.OUTPUT_SIZE_LIMIT, counts.reason());
            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS,
                    "the document stays inside the budget it reports");
        }

        /** Each collection is measured against what the document already holds, not against zero. */
        @Test
        void chargesEveryCollectionAgainstTheSameBudget() {
            int rowWidth = 4_000;
            int rows = McpToolOutput.MAX_CHARS / rowWidth + 20;
            EvidenceOutput output = new EvidenceOutput(SKELETON, rows);
            List<String> events = output.rows("events", wideRows(rows, rowWidth));
            List<String> threads = output.rows("threads", wideRows(rows, rowWidth));

            assertEquals(0, output.truncation().get("threads").returned(),
                    "the first collection spent the budget, so the second reports nothing rather than overrunning");
            assertTrue(McpToolResult.of(new Document(1, output.truncation(), events, threads)).text().length()
                    <= McpToolOutput.MAX_CHARS);
        }

        /** What the skeleton already holds is charged before the first row. */
        @Test
        void chargesTheSkeletonBeforeAnyRow() {
            int rowWidth = 4_000;
            int rows = McpToolOutput.MAX_CHARS / rowWidth + 20;
            Document heavy = new Document(1, Map.of(), List.of("y".repeat(McpToolOutput.MAX_CHARS / 2)), List.of());
            EvidenceOutput light = new EvidenceOutput(SKELETON, rows);
            EvidenceOutput loaded = new EvidenceOutput(heavy, rows);

            assertTrue(loaded.rows("threads", wideRows(rows, rowWidth)).size()
                    < light.rows("threads", wideRows(rows, rowWidth)).size());
        }
    }

    @Test
    void keepsTheCollectionsInTheOrderTheyWereBounded() {
        EvidenceOutput output = new EvidenceOutput(SKELETON, ROW_LIMIT);
        output.rows("threads", List.of());
        output.rows("events", List.of());

        assertEquals(List.of("threads", "events"), List.copyOf(output.truncation().keySet()));
    }

    @Test
    void theTruncationMapFitsTheGeneratedSchema() {
        EvidenceOutput output = new EvidenceOutput(SKELETON, ROW_LIMIT);
        List<String> events = output.rows("events", wideRows(ROW_LIMIT + 1, 8));

        McpSchemaConformance.assertConforms(Json.toTree(new Document(1, output.truncation(), events, List.of())),
                McpSchemaGenerator.schemaOf(Document.class));
    }
}
