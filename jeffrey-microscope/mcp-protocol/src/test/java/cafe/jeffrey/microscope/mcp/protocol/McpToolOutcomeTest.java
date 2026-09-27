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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolOutcomeTest {

    private static final McpFormSchema CONFIRM = McpFormSchema.builder()
            .required(new McpFormSchema.BooleanField(new McpFormSchema.Label("confirm", "Confirm", null), false))
            .build();

    @Nested
    class RequireComplete {

        @Test
        void aResultIsItsOwnCompleteAnswer() {
            McpToolResult result = McpToolResult.text("done");

            assertSame(result, result.requireComplete());
        }

        /** A caller that cannot carry a question — a resource read — must not get one silently dropped. */
        @Test
        void aQuestionIsNotAnAnswer() {
            McpToolOutcome asking = new McpToolOutcome.InputRequired(
                    Map.of("confirm", new McpFormElicitation("Sure?", CONFIRM)));

            assertThrows(IllegalStateException.class, asking::requireComplete);
        }

        @Test
        void aTaskIsNotAnAnswer() {
            assertThrows(IllegalStateException.class, () -> new McpToolOutcome.Deferred("op-1").requireComplete());
        }
    }

    @Nested
    class Validation {

        @Test
        void refusesAQuestionWithNothingToAsk() {
            assertThrows(IllegalArgumentException.class, () -> new McpToolOutcome.InputRequired(Map.of()));
        }

        @Test
        void refusesATaskWithoutAnId() {
            assertThrows(IllegalArgumentException.class, () -> new McpToolOutcome.Deferred(" "));
        }

        @Test
        void keepsTheQuestionsInTheOrderTheyWereAsked() {
            Map<String, McpInputRequest> requests = new LinkedHashMap<>();
            requests.put("window", new McpFormElicitation("Which window?", CONFIRM));
            requests.put("confirm", new McpFormElicitation("Sure?", CONFIRM));

            McpToolOutcome.InputRequired asking = new McpToolOutcome.InputRequired(requests);
            requests.clear();

            assertEquals(List.of("window", "confirm"), List.copyOf(asking.requests().keySet()));
        }
    }
}
