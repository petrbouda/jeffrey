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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a tool answers with: a result, a question it needs answered first, or a task still running.
 * <p>
 * The tool decides which from the {@link McpCallContext} it was handed — it never asks a client that
 * cannot answer, and never defers for one that does not speak tasks. The envelope renders each.
 */
public sealed interface McpToolOutcome
        permits McpToolResult, McpToolOutcome.InputRequired, McpToolOutcome.Deferred {

    /**
     * This outcome as a finished result, for a caller that has nowhere to put anything else — a resource
     * read or a completion.
     *
     * @throws IllegalStateException when the tool asked a question or deferred; that is a bug in the
     *                               tool, which was told the caller could do neither
     */
    McpToolResult requireComplete();

    /**
     * The tool needs the user's answers first. The client asks, then retries the same call with the
     * answers under the same keys.
     *
     * @param requests the questions, by the key the answer comes back under, in the order asked
     */
    record InputRequired(Map<String, McpInputRequest> requests) implements McpToolOutcome {

        public InputRequired {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("An input request needs at least one question");
            }
            requests.keySet().forEach(InputRequired::requireKey);
            requests = Collections.unmodifiableMap(new LinkedHashMap<>(requests));
        }

        private static void requireKey(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("An input request needs a key to read its answer by");
            }
        }

        @Override
        public McpToolResult requireComplete() {
            throw new IllegalStateException("The tool asked for input where no answer can be given: "
                    + requests.keySet());
        }
    }

    /**
     * The work is still running and is reachable as a task with this id.
     */
    record Deferred(String taskId) implements McpToolOutcome {

        public Deferred {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("A deferred answer needs the id of its task");
            }
        }

        @Override
        public McpToolResult requireComplete() {
            throw new IllegalStateException("The tool deferred to a task where no task can be followed: " + taskId);
        }
    }
}
