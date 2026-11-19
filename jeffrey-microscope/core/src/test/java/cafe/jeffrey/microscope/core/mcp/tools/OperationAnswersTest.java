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

import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.NO_TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationAnswersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);
    private static final String RUNNING = "{\"status\":\"running\"}";

    private final McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
    private final OperationAnswers answers = new OperationAnswers(BoundedJobs.TASK_WAIT_BUDGET);

    private String registeredOperation() {
        BoundedJobs<String, String> jobs =
                ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, CLOCK);
        return operations.register(OperationKind.HEAP_OQL, jobs.rememberCompleted("key", "rows"), OperationResults.Value::new);
    }

    @Nested
    class WaitBudget {

        @Test
        void aTaskIsHandedBackAfterFiveSeconds() {
            assertEquals(Duration.ofSeconds(5), BoundedJobs.TASK_WAIT_BUDGET);
        }

        @Test
        void aTaskCapableClientWaitsOnlyTheTaskBudget() {
            assertEquals(BoundedJobs.TASK_WAIT_BUDGET, answers.waitBudget(TASKS, BoundedJobs.WAIT_BUDGET));
        }

        /** The shorter of the two: a tool configured below five seconds is not made to wait longer. */
        @Test
        void aStandardBudgetShorterThanTheTaskBudgetIsKept() {
            Duration shorter = BoundedJobs.TASK_WAIT_BUDGET.minusMillis(1);

            assertEquals(shorter, answers.waitBudget(TASKS, shorter));
        }

        @Test
        void aClientWithoutTheExtensionWaitsTheStandardBudget() {
            assertEquals(BoundedJobs.WAIT_BUDGET, answers.waitBudget(NO_TASKS, BoundedJobs.WAIT_BUDGET));
        }

        /** Resource reads and the Java overloads run with no capabilities, and keep today's wait. */
        @Test
        void aServerSideCallerWaitsTheStandardBudget() {
            Duration configured = Duration.ofSeconds(12);

            assertEquals(configured, answers.waitBudget(McpCallContext.RESOURCE_READ, configured));
        }
    }

    /** The task wait can be given, which is what lets a test see a deferral without waiting five seconds. */
    @Nested
    class InjectedTaskWait {

        private final Duration injected = Duration.ofMillis(100);

        @Test
        void aTaskCapableClientWaitsTheInjectedBudget() {
            assertEquals(injected,
                    new OperationAnswers(injected).waitBudget(TASKS, BoundedJobs.WAIT_BUDGET));
        }

        @Test
        void aClientWithoutTheExtensionStillWaitsTheStandardBudget() {
            assertEquals(BoundedJobs.WAIT_BUDGET,
                    new OperationAnswers(injected).waitBudget(NO_TASKS, BoundedJobs.WAIT_BUDGET));
        }

        @Test
        void rejectsAMissingBudget() {
            assertThrows(IllegalArgumentException.class, () -> new OperationAnswers(null));
        }

        @Test
        void rejectsABudgetThatIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () -> new OperationAnswers(Duration.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> new OperationAnswers(Duration.ofMillis(-1)));
        }
    }

    @Nested
    class StillRunning {

        /** A tool that answers with a record hands a task-capable client the task, and never builds its answer. */
        @Test
        void aTaskCapableClientOfARecordToolIsHandedTheTaskWithoutTheAnswerBeingBuilt() {
            String operationId = registeredOperation();

            McpToolOutcome outcome = answers.stillRunning(TASKS, operationId, () -> {
                throw new AssertionError("the polled answer is for a client that polls");
            });

            assertEquals(operationId, assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId());
        }

        @Test
        void aClientThatPollsARecordToolGetsItsOwnAnswer() {
            String operationId = registeredOperation();
            McpToolResult polled = McpToolResult.text(RUNNING);

            assertSame(polled, answers.stillRunning(NO_TASKS, operationId, () -> polled));
            assertSame(polled, answers.stillRunning(McpCallContext.RESOURCE_READ, operationId, () -> polled));
        }
    }
}
