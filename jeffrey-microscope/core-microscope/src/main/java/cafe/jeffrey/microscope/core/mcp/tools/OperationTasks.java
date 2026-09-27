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

import cafe.jeffrey.microscope.mcp.protocol.McpErrorCode;
import cafe.jeffrey.microscope.mcp.protocol.McpProtocolException;
import cafe.jeffrey.microscope.mcp.protocol.McpTask;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The operation registry served as MCP tasks: the operationId is the taskId, and a task is reachable
 * on exactly the terms its operation is reachable through {@code operations_status} and
 * {@code operations_cancel} — only when the family that started it is served.
 * <p>
 * A task that does not exist, has expired, or belongs to a withheld family is one and the same answer,
 * {@code -32602}: telling them apart would tell a client which families this installation hides.
 */
public final class OperationTasks implements McpTaskProvider {

    private static final String UNKNOWN_TASK = "Unknown or expired task: %s. Tasks are process-local and "
            + "retained for one hour after completion.";

    private final McpOperationRegistry operations;
    private final Predicate<OperationKind> allowedKind;

    /**
     * @param allowedKind the family gate {@code operations_*} apply, so both views reach the same work
     */
    public OperationTasks(McpOperationRegistry operations, Predicate<OperationKind> allowedKind) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.allowedKind = Objects.requireNonNull(allowedKind, "allowedKind");
    }

    /**
     * The task provider an endpoint serving these kinds should declare: {@link McpTaskProvider#NONE}
     * when it serves no family that starts an operation, since a tasks extension that can never hand
     * out a task is a capability advertised for nothing.
     */
    public static McpTaskProvider of(McpOperationRegistry operations, Predicate<OperationKind> allowedKind) {
        return followsAny(allowedKind) ? new OperationTasks(operations, allowedKind) : McpTaskProvider.NONE;
    }

    /** Whether any operation kind passes the gate: whether there is anything to follow as a task. */
    public static boolean followsAny(Predicate<OperationKind> allowedKind) {
        return Arrays.stream(OperationKind.values()).anyMatch(allowedKind);
    }

    @Override
    public McpTask get(String taskId) {
        try {
            return operations.task(taskId, allowedKind);
        } catch (IllegalArgumentException unknown) {
            throw unknownTask(taskId);
        }
    }

    @Override
    public void cancel(String taskId) {
        try {
            operations.cancel(taskId, allowedKind);
        } catch (IllegalArgumentException unknown) {
            throw unknownTask(taskId);
        }
    }

    private static McpProtocolException unknownTask(String taskId) {
        return new McpProtocolException(McpErrorCode.INVALID_PARAMS, UNKNOWN_TASK.formatted(taskId));
    }
}
