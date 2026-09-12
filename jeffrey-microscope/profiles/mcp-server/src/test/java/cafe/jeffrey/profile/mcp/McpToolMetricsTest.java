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
package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolMetricsTest {
    @Test
    void reportsCallsOmittedByTheFixedCardinalityLimit() {
        McpToolMetrics metrics = new McpToolMetrics();
        for (int index = 0; index < 257; index++) {
            metrics.record("tool_" + index, 1, 1, false);
        }
        assertEquals(256, metrics.snapshot().size());
        assertEquals(1, metrics.droppedCalls());
    }

    @Test
    void aggregatesCountsSizesAndDurationsWithoutKeepingIndividualCalls() {
        McpToolMetrics metrics = new McpToolMetrics();
        metrics.record("test_read", 10, 20, false);
        metrics.record("test_read", 30, 40, true);
        McpToolMetrics.Sample row = metrics.snapshot().getFirst();
        assertEquals(2, row.calls());
        assertEquals(1, row.errors());
        assertEquals(40, row.totalDurationNanos());
        assertEquals(30, row.maxDurationNanos());
        assertEquals(60, row.totalOutputBytes());
        assertEquals(40, row.maxOutputBytes());
    }

    @Test
    void measuresActualUtf8ResultEnvelopeAndFailuresButNotUnknownNames() {
        MetricsController controller = new MetricsController();
        ReflectiveToolset tools = new ReflectiveToolset(new Samples(), "sample");
        JsonNode success = call(controller, tools, "sample_read");
        call(controller, tools, "sample_fail");
        call(controller, tools, "attacker_secret");
        List<McpToolMetrics.Sample> rows = controller.samples();
        assertEquals(2, rows.size());
        McpToolMetrics.Sample read = rows.stream().filter(r -> r.tool().equals("sample_read")).findFirst().orElseThrow();
        assertEquals(Json.toByteArray(success.path("result")).length, read.totalOutputBytes());
        assertEquals(0, read.errors());
        assertTrue(read.totalDurationNanos() > 0);
        assertEquals(1, rows.stream().filter(r -> r.tool().equals("sample_fail")).findFirst().orElseThrow().errors());
        assertTrue(rows.stream().noneMatch(r -> r.tool().contains("secret")));
    }

    private static JsonNode call(MetricsController controller, McpToolProvider tools, String name) {
        ObjectNode request = Json.createObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
        request.set("params", Json.createObject().put("name", name));
        return controller.dispatch(request, "2025-06-18", new McpServerFeatures(() -> tools, () -> null, () -> null)).getBody();
    }

    static class MetricsController extends AbstractMcpStreamableHttpController {
        List<McpToolMetrics.Sample> samples() {
            return toolMetrics().snapshot();
        }
    }

    static class Samples {
        @Tool(description = "Read a Unicode sample")
        public String read() {
            return "こんにちは";
        }

        @Tool(description = "Fail a sample")
        public String fail() {
            throw new IllegalArgumentException("failure");
        }
    }
}
