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
 * The status a task reports over the {@code io.modelcontextprotocol/tasks} extension, by the name the
 * specification gives it on the wire.
 * <p>
 * All five are listed because they are the protocol's vocabulary, not because every one is produced
 * today: {@link #INPUT_REQUIRED} waits for mid-flight input, which this server does not ask for yet, and
 * {@link #FAILED} is reserved for a task whose answer could not be rendered — a tool that ran and
 * failed is {@link #COMPLETED}, with {@code isError} inside its result.
 */
public enum McpTaskStatus {

    WORKING("working"),
    INPUT_REQUIRED("input_required"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String wireName;

    McpTaskStatus(String wireName) {
        this.wireName = wireName;
    }

    /** The status as the {@code status} field spells it. */
    public String wireName() {
        return wireName;
    }
}
