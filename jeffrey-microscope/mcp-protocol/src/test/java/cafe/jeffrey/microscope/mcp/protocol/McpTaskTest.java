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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpTaskTest {

    private static final Instant CREATED = Instant.parse("2026-09-26T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-26T10:00:07.250Z");
    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration POLL = Duration.ofSeconds(5);

    private static McpTask task(McpTaskState state) {
        return new McpTask("op-1", state, CREATED, UPDATED, TTL, POLL);
    }

    @Nested
    class Statuses {

        @Test
        void wireNamesAreTheSpecificationsFive() {
            assertEquals(List.of("working", "input_required", "completed", "failed", "cancelled"),
                    Arrays.stream(McpTaskStatus.values()).map(McpTaskStatus::wireName).toList());
        }

        @Test
        void eachStateReportsItsStatus() {
            assertEquals(McpTaskStatus.WORKING, new McpTaskState.Working("queued").status());
            assertEquals(McpTaskStatus.COMPLETED, new McpTaskState.Completed(McpToolResult.text("done")).status());
            assertEquals(McpTaskStatus.CANCELLED, new McpTaskState.Cancelled("cancelled").status());
        }

        /** A tool that failed still answered: the task completed, with isError inside its result. */
        @Test
        void aToolFailureIsACompletedTask() {
            assertEquals(McpTaskStatus.COMPLETED,
                    new McpTaskState.ToolFailed(new IllegalStateException("boom")).status());
        }

        @Test
        void onlyWorkingAndCancelledCarryAStatusMessage() {
            assertEquals("phase", new McpTaskState.Working("phase").statusMessage());
            assertEquals("stopped", new McpTaskState.Cancelled("stopped").statusMessage());
            assertNull(new McpTaskState.Completed(McpToolResult.text("done")).statusMessage());
            assertNull(new McpTaskState.ToolFailed(new IllegalStateException("boom")).statusMessage());
        }

        @Test
        void refusesACompletionWithoutAResultOrAFailureWithoutACause() {
            assertThrows(IllegalArgumentException.class, () -> new McpTaskState.Completed(null));
            assertThrows(IllegalArgumentException.class, () -> new McpTaskState.ToolFailed(null));
        }
    }

    @Nested
    class Fields {

        @Test
        void rendersTheCreateTaskResultFields() {
            ObjectNode json = task(new McpTaskState.Working("downloading")).toJson();

            assertEquals(McpJson.readTree("""
                    {"taskId":"op-1","status":"working","statusMessage":"downloading",
                     "createdAt":"2026-09-26T10:00:00Z","lastUpdatedAt":"2026-09-26T10:00:07.250Z",
                     "ttlMs":3600000,"pollIntervalMs":5000}
                    """).toString(), json.toString());
        }

        @Test
        void leavesOutTheOptionalFieldsWhenAbsent() {
            ObjectNode json = new McpTask("op-1", new McpTaskState.Working(null), CREATED, CREATED, TTL, null)
                    .toJson();

            assertFalse(json.has("statusMessage"));
            assertFalse(json.has("pollIntervalMs"));
        }

        /** The task fields never carry the finished result: tasks/get adds it. */
        @Test
        void aCompletedTaskRendersOnlyItsFields() {
            ObjectNode json = task(new McpTaskState.Completed(McpToolResult.text("done"))).toJson();

            assertEquals("completed", json.path("status").asString());
            assertFalse(json.has("result"));
            assertFalse(json.has("statusMessage"));
        }
    }

    @Nested
    class Validation {

        @Test
        void refusesABlankId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new McpTask(" ", new McpTaskState.Working(null), CREATED, CREATED, TTL, POLL));
        }

        @Test
        void refusesMissingParts() {
            McpTaskState working = new McpTaskState.Working(null);
            assertThrows(IllegalArgumentException.class, () -> new McpTask("op-1", null, CREATED, CREATED, TTL, POLL));
            assertThrows(IllegalArgumentException.class, () -> new McpTask("op-1", working, null, CREATED, TTL, POLL));
            assertThrows(IllegalArgumentException.class, () -> new McpTask("op-1", working, CREATED, null, TTL, POLL));
            assertThrows(IllegalArgumentException.class, () -> new McpTask("op-1", working, CREATED, CREATED, null, POLL));
        }

        @Test
        void refusesNegativeDurations() {
            McpTaskState working = new McpTaskState.Working(null);
            assertThrows(IllegalArgumentException.class,
                    () -> new McpTask("op-1", working, CREATED, CREATED, Duration.ofSeconds(-1), POLL));
            assertThrows(IllegalArgumentException.class,
                    () -> new McpTask("op-1", working, CREATED, CREATED, TTL, Duration.ofSeconds(-1)));
        }

        @Test
        void refusesAnUpdateBeforeTheCreation() {
            assertThrows(IllegalArgumentException.class, () -> new McpTask(
                    "op-1", new McpTaskState.Working(null), UPDATED, CREATED, TTL, POLL));
        }
    }
}
