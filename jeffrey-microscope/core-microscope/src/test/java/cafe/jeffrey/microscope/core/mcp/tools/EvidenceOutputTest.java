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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound on an evidence document, which decides what a caller actually receives when a profile
 * holds more than fits. The figures are the point: a collection says how much it dropped and why.
 */
class EvidenceOutputTest {

    private static final int ROW_LIMIT = 100;

    private final ObjectNode root = Json.createObject().put("schemaVersion", 1);

    /** Rows wide enough that a few hundred of them pass the output budget. */
    private static List<String> wideRows(int count, int width) {
        return IntStream.range(0, count).mapToObj(index -> index + "-" + "x".repeat(width)).toList();
    }

    @Nested
    class Truncation {

        @Test
        void keepsEverythingWhenItFits() {
            EvidenceOutput output = new EvidenceOutput(root, ROW_LIMIT);
            output.rows("events", wideRows(10, 8));

            ObjectNode counts = (ObjectNode) output.result().structuredContent().get("truncation").get("events");
            assertEquals(10, counts.get("total").asInt());
            assertEquals(10, counts.get("returned").asInt());
            assertEquals(0, counts.get("omitted").asInt());
            assertEquals("complete", counts.get("reason").asString());
        }

        @Test
        void stopsAtTheRowLimitAndSaysSo() {
            EvidenceOutput output = new EvidenceOutput(root, ROW_LIMIT);
            output.rows("events", wideRows(ROW_LIMIT + 40, 8));

            ObjectNode counts = (ObjectNode) output.result().structuredContent().get("truncation").get("events");
            assertEquals(ROW_LIMIT + 40, counts.get("total").asInt());
            assertEquals(ROW_LIMIT, counts.get("returned").asInt());
            assertEquals(40, counts.get("omitted").asInt());
            assertEquals("row-limit", counts.get("reason").asString());
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
            EvidenceOutput output = new EvidenceOutput(root, rows);
            output.rows("events", wideRows(rows, rowWidth));

            McpToolResult result = output.result();
            ObjectNode counts = (ObjectNode) result.structuredContent().get("truncation").get("events");
            assertEquals(rows, counts.get("total").asInt());
            assertTrue(counts.get("returned").asInt() > 0, "a budget that fits several rows returns some");
            assertTrue(counts.get("omitted").asInt() > 0, "and drops the ones past it");
            assertEquals("output-size-limit", counts.get("reason").asString());
            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS,
                    "the document stays inside the budget it reports");
        }

        /** Each collection is measured against what the document already holds, not against zero. */
        @Test
        void chargesEveryCollectionAgainstTheSameBudget() {
            int rowWidth = 4_000;
            int rows = McpToolOutput.MAX_CHARS / rowWidth + 20;
            EvidenceOutput output = new EvidenceOutput(root, rows);
            output.rows("events", wideRows(rows, rowWidth));
            output.rows("threads", wideRows(rows, rowWidth));

            ObjectNode truncation = (ObjectNode) output.result().structuredContent().get("truncation");
            assertEquals(0, truncation.get("threads").get("returned").asInt(),
                    "the first collection spent the budget, so the second reports nothing rather than overrunning");
            assertTrue(output.result().text().length() <= McpToolOutput.MAX_CHARS);
        }
    }
}
