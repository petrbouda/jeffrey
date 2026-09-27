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
 * Where one task stands: still working, answered, answered with a tool failure, or cancelled.
 * <p>
 * The provider decides the state; the envelope renders it. A finished task carries what the envelope
 * needs to render the full {@code tools/call} result — the tool's answer, or the failure that
 * {@code tools/call} would have turned into an {@code isError} result — so the answer a client gets by
 * following a task is the one it would have got by waiting.
 */
public sealed interface McpTaskState
        permits McpTaskState.Working, McpTaskState.Completed, McpTaskState.ToolFailed, McpTaskState.Cancelled {

    /** The status this state reports on the wire. */
    McpTaskStatus status();

    /** A sentence for whoever watches the task, or null when the state has none to add. */
    default String statusMessage() {
        return null;
    }

    /**
     * The work is still running.
     *
     * @param statusMessage what it is doing now, or null
     */
    record Working(String statusMessage) implements McpTaskState {

        @Override
        public McpTaskStatus status() {
            return McpTaskStatus.WORKING;
        }
    }

    /**
     * The tool answered.
     *
     * @param result the answer, rendered as {@code tools/call} renders it
     */
    record Completed(McpToolResult result) implements McpTaskState {

        public Completed {
            if (result == null) {
                throw new IllegalArgumentException("A completed task needs the tool's answer");
            }
        }

        @Override
        public McpTaskStatus status() {
            return McpTaskStatus.COMPLETED;
        }
    }

    /**
     * The tool ran and failed. Still a completed task: the failure is the answer, rendered as the
     * {@code isError} result {@code tools/call} would have given, in the words the caller may be given.
     *
     * @param failure what the tool or its work threw
     */
    record ToolFailed(Throwable failure) implements McpTaskState {

        public ToolFailed {
            if (failure == null) {
                throw new IllegalArgumentException("A failed tool needs the failure it ended with");
            }
        }

        @Override
        public McpTaskStatus status() {
            return McpTaskStatus.COMPLETED;
        }
    }

    /**
     * The work was stopped before it answered.
     *
     * @param statusMessage why, or null
     */
    record Cancelled(String statusMessage) implements McpTaskState {

        @Override
        public McpTaskStatus status() {
            return McpTaskStatus.CANCELLED;
        }
    }
}
