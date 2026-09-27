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

package cafe.jeffrey.microscope.mcp.protocol;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpResultsTest {

    private static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    private static final McpServerIdentity SERVER = new McpServerIdentity("example", "1.2.3");

    private static final McpResults RESULTS = new McpResults(SERVER);

    private static final McpClientCapabilities ELICITING =
            McpClientCapabilities.parse(McpJson.readTree("{\"elicitation\":{}}"));

    private static final McpFormSchema CONFIRM = McpFormSchema.builder()
            .required(new McpFormSchema.BooleanField(new McpFormSchema.Label("confirm", "Confirm", null), false))
            .build();

    @Nested
    class Complete {

        @Test
        void marksTheResultAndSaysWhichServerAnswered() {
            ObjectNode result = McpJson.createObject().put("answer", 42);

            ObjectNode completed = RESULTS.complete(result, McpCacheHint.NONE);

            assertSame(result, completed);
            assertEquals("complete", completed.get("resultType").asString());
            assertEquals("example", completed.get("_meta").get(SERVER_INFO).get("name").asString());
            assertEquals("1.2.3", completed.get("_meta").get(SERVER_INFO).get("version").asString());
            assertEquals(42, completed.get("answer").asInt());
        }

        /** {@code tools/call}, {@code prompts/get} and {@code completion/complete} carry no hint. */
        @Test
        void addsNoCacheHintForNone() {
            ObjectNode completed = RESULTS.complete(McpJson.createObject(), McpCacheHint.NONE);

            assertFalse(completed.has("ttlMs"));
            assertFalse(completed.has("cacheScope"));
        }

        @Test
        void addsAStaticHint() {
            ObjectNode completed = RESULTS.complete(McpJson.createObject(), McpCacheHint.STATIC);

            assertEquals(3_600_000L, completed.get("ttlMs").asLong());
            assertEquals("public", completed.get("cacheScope").asString());
        }

        /** A zero lifetime is still a hint the specification requires on {@code resources/read}. */
        @Test
        void addsADynamicHint() {
            ObjectNode completed = RESULTS.complete(McpJson.createObject(), McpCacheHint.DYNAMIC);

            assertEquals(0L, completed.get("ttlMs").asLong());
            assertEquals("private", completed.get("cacheScope").asString());
        }

        @Test
        void keepsMetaTheResultAlreadyCarried() {
            ObjectNode result = McpJson.createObject();
            result.putObject("_meta").put("example/elapsedMs", 5);

            ObjectNode completed = RESULTS.complete(result, McpCacheHint.NONE);

            assertEquals(5, completed.get("_meta").get("example/elapsedMs").asInt());
            assertTrue(completed.get("_meta").has(SERVER_INFO));
        }

        @Test
        void refusesAMissingHint() {
            assertThrows(NullPointerException.class, () -> RESULTS.complete(McpJson.createObject(), null));
        }
    }

    @Nested
    class InputRequired {

        /** Input requests are never cached: the answer is the user's, and the retry is a new request. */
        @Test
        void marksTheResultWithoutCacheHints() {
            ObjectNode result = McpJson.createObject();
            result.putObject("inputRequests");

            ObjectNode asking = RESULTS.inputRequired(result);

            assertSame(result, asking);
            assertEquals("input_required", asking.get("resultType").asString());
            assertTrue(asking.get("_meta").has(SERVER_INFO));
            assertFalse(asking.has("ttlMs"));
            assertFalse(asking.has("cacheScope"));
        }

        @Test
        void rendersAToolsQuestions() {
            McpToolOutcome.InputRequired questions = new McpToolOutcome.InputRequired(Map.of(
                    "confirm", new McpFormElicitation("Sure?", CONFIRM)));

            ObjectNode result = RESULTS.inputRequired(questions, ELICITING);

            assertEquals("input_required", result.get("resultType").asString());
            assertEquals(List.of("confirm"), List.copyOf(result.get("inputRequests").propertyNames()));
            ObjectNode request = (ObjectNode) result.get("inputRequests").get("confirm");
            assertEquals("elicitation/create", request.get("method").asString());
            assertEquals("form", request.get("params").get("mode").asString());
            assertEquals("Sure?", request.get("params").get("message").asString());
            assertEquals("object", request.get("params").get("requestedSchema").get("type").asString());
            assertTrue(result.get("_meta").has(SERVER_INFO));
            assertFalse(result.has("ttlMs"));
        }

        /** An elicitation the client did not declare must never be sent. */
        @Test
        void refusesAQuestionTheClientCannotAnswer() {
            McpToolOutcome.InputRequired questions = new McpToolOutcome.InputRequired(Map.of(
                    "confirm", new McpFormElicitation("Sure?", CONFIRM)));

            assertThrows(IllegalStateException.class,
                    () -> RESULTS.inputRequired(questions, McpClientCapabilities.NONE));
        }
    }

    @Nested
    class Task {

        /** A task's {@code ttlMs} is its retention, set by the caller; no cache hint is added. */
        @Test
        void marksATaskAndKeepsItsFields() {
            ObjectNode task = McpJson.createObject().put("taskId", "op-1").put("status", "working");

            ObjectNode result = RESULTS.task(task);

            assertSame(task, result);
            assertEquals("task", result.get("resultType").asString());
            assertEquals("op-1", result.get("taskId").asString());
            assertEquals("working", result.get("status").asString());
            assertTrue(result.get("_meta").has(SERVER_INFO));
            assertFalse(result.has("cacheScope"));
        }
    }
}
