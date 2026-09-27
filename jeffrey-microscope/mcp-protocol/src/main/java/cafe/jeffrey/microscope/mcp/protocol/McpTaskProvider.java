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

/**
 * The tasks one endpoint follows over the {@code io.modelcontextprotocol/tasks} extension: what a
 * {@code tools/call} whose tool answered {@link McpToolOutcome.Deferred} hands back, and what
 * {@code tasks/get}, {@code tasks/update} and {@code tasks/cancel} read and stop.
 * <p>
 * An endpoint holding {@link #NONE} declares no {@code tasks} extension at {@code server/discover}.
 */
public interface McpTaskProvider {

    /** Follows no tasks: the extension is not declared, and no id names a task. */
    McpTaskProvider NONE = new McpTaskProvider() {

        private static final String NO_TASKS = "Unknown task: %s. This endpoint follows no tasks";

        @Override
        public McpTask get(String taskId) {
            throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, NO_TASKS.formatted(taskId));
        }

        @Override
        public void cancel(String taskId) {
            throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, NO_TASKS.formatted(taskId));
        }
    };

    /**
     * The task as it stands now.
     *
     * @throws McpProtocolException {@code -32602} when no task has this id, it has expired, or this
     *                              endpoint does not serve the work behind it
     */
    McpTask get(String taskId);

    /**
     * Asks the work behind a task to stop. Best effort, and never waits for it: the task reports
     * {@code cancelled} once the work has actually stopped.
     *
     * @throws McpProtocolException {@code -32602} on the same terms as {@link #get}
     */
    void cancel(String taskId);
}
