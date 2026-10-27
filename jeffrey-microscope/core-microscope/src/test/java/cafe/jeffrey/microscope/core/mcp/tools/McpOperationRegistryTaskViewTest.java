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

import cafe.jeffrey.microscope.mcp.protocol.McpTask;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationSnapshot;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.MutableClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The task view of the operation registry: one operation read as an MCP task, the operationId as the
 * taskId, beside the {@code operations_*} view of the same entry.
 */
class McpOperationRegistryTaskViewTest {

    private static final Instant STARTED = Instant.parse("2026-09-26T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-09-26T10:03:00Z");

    private final MutableClock clock = new MutableClock(STARTED.plusSeconds(1), ZoneOffset.UTC);
    private final McpOperationRegistry registry = new McpOperationRegistry(clock);
    private final FakeOperation operation = new FakeOperation("op-1");

    private McpTask task() {
        return registry.task("op-1", kind -> true);
    }

    @Nested
    class WhileWorking {

        @Test
        void aQueuedOperationIsAWorkingTaskSayingQueued() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);

            McpTask task = task();

            assertEquals(new McpTaskState.Working("queued"), task.state());
            assertEquals(McpTaskStatus.WORKING, task.status());
        }

        @Test
        void aRunningOperationReportsItsPhase() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);
            operation.running(OperationPhase.DOWNLOADING.code());

            assertEquals(new McpTaskState.Working(OperationPhase.DOWNLOADING.code()), task().state());
        }

        @Test
        void aCancellationStillInFlightIsWorking() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);
            operation.cancelRequested();

            assertEquals(new McpTaskState.Working("cancellation requested"), task().state());
        }

        /** The operationId is the taskId, and the timing fields come straight off the operation. */
        @Test
        void carriesTheOperationsIdentityAndTiming() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);

            McpTask task = task();

            assertEquals("op-1", task.taskId());
            assertEquals(STARTED, task.createdAt());
            assertEquals(STARTED, task.lastUpdatedAt());
            assertEquals(McpOperationRegistry.RETENTION, task.ttl());
            assertEquals(Duration.ofHours(1), task.ttl());
            assertEquals(McpOperationRegistry.TASK_POLL_INTERVAL, task.pollInterval());
            assertEquals(Duration.ofSeconds(5), task.pollInterval());
        }
    }

    @Nested
    class Finished {

        @Test
        void aCompletedOperationAnswersWithItsTerminalAnswer() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new,
                    value -> McpToolResult.text("answer:" + value));
            operation.completed("profile-7");

            McpTask task = task();

            assertEquals(new McpTaskState.Completed(McpToolResult.text("answer:profile-7")), task.state());
            assertEquals(FINISHED, task.lastUpdatedAt());
            assertEquals(STARTED, task.createdAt());
        }

        /** Every kind is task-capable: without an answer of its own, the answer is the terminal snapshot. */
        @Test
        void answersWithTheTerminalSnapshotWhenNoAnswerWasGiven() {
            registry.register(OperationKind.RECORDING_ANALYSIS, operation, OperationResults.Rendered::new);
            operation.completed("profile-7");

            McpTaskState.Completed completed = assertInstanceOf(McpTaskState.Completed.class, task().state());

            // The same typed answer operations_status gives: the record as structured content and as text.
            assertEquals(Json.toTree(registry.status("op-1")), completed.result().structuredContent());
            assertEquals(Json.toString(registry.status("op-1")), completed.result().text());
            JsonNode snapshot = Json.readTree(completed.result().text());
            assertEquals("COMPLETED", snapshot.path("status").asString());
            assertEquals("profile-7", snapshot.path("result").path("rendered").asString());
        }

        @Test
        void computesTheAnswerOnceHoweverOftenItIsRead() {
            AtomicInteger answers = new AtomicInteger();
            registry.register(OperationKind.HEAP_OQL, operation, OperationResults.Value::new, value -> {
                answers.incrementAndGet();
                return McpToolResult.text(value);
            });
            operation.completed("rows");

            McpTaskState first = task().state();
            registry.status("op-1");
            McpTaskState second = task().state();

            assertEquals(1, answers.get());
            assertSame(first, second);
        }

        /** The work finished, but turning its value into an answer threw: the tool failed. */
        @Test
        void aThrowingAnswerIsAToolFailure() {
            IllegalStateException broken = new IllegalStateException("cannot render");
            registry.register(OperationKind.HEAP_OQL, operation, OperationResults.Value::new, value -> {
                throw broken;
            });
            operation.completed("rows");

            McpTaskState.ToolFailed failed = assertInstanceOf(McpTaskState.ToolFailed.class, task().state());

            assertSame(broken, failed.failure());
            assertEquals(McpTaskStatus.COMPLETED, failed.status());
            assertEquals(OperationState.COMPLETED, registry.status("op-1").status());
        }

        @Test
        void aFailedOperationIsAToolFailureCarryingItsCause() {
            IllegalArgumentException cause = new IllegalArgumentException("hub refused the session");
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);
            operation.failed(cause);

            McpTaskState.ToolFailed failed = assertInstanceOf(McpTaskState.ToolFailed.class, task().state());

            assertSame(cause, failed.failure());
            assertEquals(FINISHED, task().lastUpdatedAt());
        }

        @Test
        void aCancelledOperationIsACancelledTask() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);
            operation.cancelled();

            McpTask task = task();

            assertEquals(new McpTaskState.Cancelled("cancelled"), task.state());
            assertEquals(McpTaskStatus.CANCELLED, task.status());
            assertEquals(FINISHED, task.lastUpdatedAt());
        }
    }

    @Nested
    class Lookup {

        @Test
        void refusesAnUnknownId() {
            assertThrows(IllegalArgumentException.class, () -> registry.task("op-9", kind -> true));
        }

        @Test
        void refusesAnIdOnceItsRetentionHasPassed() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);
            operation.completed("done");
            clock.set(FINISHED.plus(McpOperationRegistry.RETENTION).plusSeconds(1));

            assertThrows(IllegalArgumentException.class, () -> registry.task("op-1", kind -> true));
        }

        @Test
        void refusesAKindTheCallerMayNotReach() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);

            assertThrows(IllegalArgumentException.class,
                    () -> registry.task("op-1", kind -> kind != OperationKind.HUB_DOWNLOAD));
        }

        @Test
        void readsTheSameEntryTheOperationsViewReads() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);

            registry.cancel("op-1", kind -> true);

            assertEquals(1, operation.cancelCount());
            assertNull(registry.status("op-1").finishedAtEpochMs());
        }
    }

    /**
     * An operation a test moves through its states. Starts queued at {@link #STARTED}; every terminal
     * state finishes at {@link #FINISHED}.
     */
    static final class FakeOperation implements OperationHandle<String> {

        private final String id;
        private final AtomicInteger cancels = new AtomicInteger();
        private volatile OperationSnapshot<String> snapshot;

        FakeOperation(String id) {
            this.id = id;
            this.snapshot = snapshot(OperationState.QUEUED, null, false, "queued", null, null);
        }

        void running(String phase) {
            snapshot = snapshot(OperationState.RUNNING, null, false, phase, null, null);
        }

        void cancelRequested() {
            snapshot = snapshot(OperationState.CANCEL_REQUESTED, null, true, "running", null, null);
        }

        void completed(String result) {
            snapshot = snapshot(OperationState.COMPLETED, FINISHED, false, "running", result, null);
        }

        void failed(RuntimeException failure) {
            snapshot = snapshot(OperationState.FAILED, FINISHED, false, "running", null, failure);
        }

        void cancelled() {
            snapshot = snapshot(OperationState.CANCELLED, FINISHED, true, "running", null,
                    new IllegalStateException("Operation cancelled"));
        }

        private OperationSnapshot<String> snapshot(
                OperationState state, Instant finished, boolean cancellationRequested,
                String phase, String result, RuntimeException failure) {
            return new OperationSnapshot<>(id, state, STARTED, finished, cancellationRequested, phase, null,
                    result, failure);
        }

        @Override
        public OperationSnapshot<String> snapshot() {
            return snapshot;
        }

        @Override
        public boolean cancel() {
            cancels.incrementAndGet();
            return true;
        }

        int cancelCount() {
            return cancels.get();
        }
    }
}
