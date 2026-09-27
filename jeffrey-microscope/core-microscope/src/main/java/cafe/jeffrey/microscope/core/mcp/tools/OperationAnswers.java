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

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * How a tool that waits on an operation answers, depending on what the client declared.
 * <p>
 * A client that declared the MCP tasks extension waits at most {@link BoundedJobs#TASK_WAIT_BUDGET}
 * and is then handed the operation as a task, which it follows with {@code tasks/get}. Every other
 * caller -- a client without the extension, or a resource read -- waits the tool's
 * standard budget and is handed the running status with the {@code operationId} for
 * {@code operations_status}, exactly as before tasks existed. A call that finishes inside its wait
 * is answered the same way for both, which is why this decides only the wait and the unfinished case.
 */
public final class OperationAnswers {

    private final Duration taskWaitBudget;

    /**
     * @param taskWaitBudget how long a client that declared tasks is kept waiting before it is handed
     *                       the task; production uses {@link BoundedJobs#TASK_WAIT_BUDGET}
     */
    public OperationAnswers(Duration taskWaitBudget) {
        if (taskWaitBudget == null || taskWaitBudget.isZero() || taskWaitBudget.isNegative()) {
            throw new IllegalArgumentException("taskWaitBudget must be positive: " + taskWaitBudget);
        }
        this.taskWaitBudget = taskWaitBudget;
    }

    /**
     * @param standardBudget what the tool waits for a caller that cannot follow a task
     * @return the shorter of the task wait and {@code standardBudget} for a client that declared
     *         tasks, else {@code standardBudget}
     */
    public Duration waitBudget(McpCallContext call, Duration standardBudget) {
        Objects.requireNonNull(standardBudget, "standardBudget");
        if (!call.tasksSupported()) {
            return standardBudget;
        }
        return taskWaitBudget.compareTo(standardBudget) < 0 ? taskWaitBudget : standardBudget;
    }

    /**
     * The answer to a call whose operation outlasted its wait, for a tool that answers with a record.
     *
     * @param polled the tool's own "still running" answer, which carries the operation itself; built
     *               only for a caller that polls
     * @return the task for a client that declared tasks; otherwise {@code polled}
     */
    public McpToolOutcome stillRunning(McpCallContext call, String operationId, Supplier<McpToolResult> polled) {
        if (call.tasksSupported()) {
            return new McpToolOutcome.Deferred(operationId);
        }
        return polled.get();
    }
}
