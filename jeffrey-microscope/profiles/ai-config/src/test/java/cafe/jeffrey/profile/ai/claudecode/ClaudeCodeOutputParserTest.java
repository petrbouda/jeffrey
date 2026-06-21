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

package cafe.jeffrey.profile.ai.claudecode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeCodeOutputParserTest {

    private final ClaudeCodeOutputParser parser = new ClaudeCodeOutputParser(new ObjectMapper());

    @Nested
    class SuccessfulOutput {

        @Test
        void extractsResultTextFromResultEvent() {
            List<String> lines = List.of(
                    "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"s1\"}",
                    "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}",
                    "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"The hottest method is foo()\"}");

            ClaudeCodeResult result = parser.parse(lines);

            assertEquals("The hottest method is foo()", result.text());
            assertFalse(result.error());
            assertTrue(result.toolsUsed().isEmpty());
        }

        @Test
        void collectsToolNamesFromAssistantMessageEvents() {
            List<String> lines = List.of(
                    "{\"type\":\"assistant\",\"message\":{\"content\":["
                            + "{\"type\":\"tool_use\",\"name\":\"mcp__jeffrey__jfr_listEventTypes\"},"
                            + "{\"type\":\"text\",\"text\":\"querying\"}]}}",
                    "{\"type\":\"assistant\",\"message\":{\"content\":["
                            + "{\"type\":\"tool_use\",\"name\":\"mcp__jeffrey__jfr_executeQuery\"}]}}",
                    "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"done\"}");

            ClaudeCodeResult result = parser.parse(lines);

            assertEquals(List.of("mcp__jeffrey__jfr_listEventTypes", "mcp__jeffrey__jfr_executeQuery"),
                    result.toolsUsed());
            assertEquals("done", result.text());
        }

        @Test
        void collectsToolNamesFromStreamEventBlocks() {
            List<String> lines = List.of(
                    "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_start\","
                            + "\"content_block\":{\"type\":\"tool_use\",\"name\":\"mcp__jeffrey__heap_getLeakSuspects\"}}}",
                    "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"ok\"}");

            ClaudeCodeResult result = parser.parse(lines);

            assertEquals(List.of("mcp__jeffrey__heap_getLeakSuspects"), result.toolsUsed());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void flagsErrorWhenIsErrorTrue() {
            List<String> lines = List.of(
                    "{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,\"result\":\"boom\"}");

            ClaudeCodeResult result = parser.parse(lines);

            assertTrue(result.error());
            assertEquals("boom", result.text());
        }

        @Test
        void skipsNonJsonLines() {
            List<String> lines = List.of(
                    "not json at all",
                    "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"recovered\"}");

            ClaudeCodeResult result = parser.parse(lines);

            assertFalse(result.error());
            assertEquals("recovered", result.text());
        }

        @Test
        void returnsFailureWhenErrorWithoutText() {
            List<String> lines = List.of(
                    "{\"type\":\"result\",\"subtype\":\"error_max_turns\",\"is_error\":true,\"result\":\"\"}");

            ClaudeCodeResult result = parser.parse(lines);

            assertTrue(result.error());
            assertFalse(result.text().isBlank());
        }
    }
}
