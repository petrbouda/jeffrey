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

import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistryTaskViewTest.FakeOperation;
import cafe.jeffrey.microscope.mcp.protocol.McpErrorCode;
import cafe.jeffrey.microscope.mcp.protocol.McpProtocolException;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTasksTest {

    private final McpOperationRegistry registry = new McpOperationRegistry(
            Clock.fixed(Instant.parse("2026-09-26T10:00:01Z"), ZoneOffset.UTC));
    private final FakeOperation operation = new FakeOperation("op-1");

    @Nested
    class Following {

        private final McpTaskProvider tasks = new OperationTasks(registry, kind -> true);

        @Test
        void readsTheOperationAsATask() {
            registry.register(OperationKind.RECORDING_IMPORT, operation, OperationResults.Value::new);

            assertEquals(new McpTaskState.Working("queued"), tasks.get("op-1").state());
            assertEquals("op-1", tasks.get("op-1").taskId());
        }

        @Test
        void cancelsTheOperationBehindTheTask() {
            registry.register(OperationKind.RECORDING_IMPORT, operation, OperationResults.Value::new);

            tasks.cancel("op-1");

            assertEquals(1, operation.cancelCount());
        }

        @Test
        void refusesAnUnknownIdAsInvalidParams() {
            McpProtocolException get = assertThrows(McpProtocolException.class, () -> tasks.get("op-9"));
            McpProtocolException cancel = assertThrows(McpProtocolException.class, () -> tasks.cancel("op-9"));

            assertEquals(McpErrorCode.INVALID_PARAMS, get.code());
            assertEquals(McpErrorCode.INVALID_PARAMS, cancel.code());
            assertTrue(get.getMessage().contains("op-9"), get.getMessage());
        }
    }

    /** The same family gate operations_status and operations_cancel apply. */
    @Nested
    class Gated {

        private final McpTaskProvider tasks = new OperationTasks(registry, kind -> !kind.reachesHub());

        @Test
        void refusesATaskOfAFamilyThisEndpointDoesNotServe() {
            registry.register(OperationKind.HUB_DOWNLOAD, operation, OperationResults.Value::new);

            assertEquals(McpErrorCode.INVALID_PARAMS,
                    assertThrows(McpProtocolException.class, () -> tasks.get("op-1")).code());
            assertEquals(McpErrorCode.INVALID_PARAMS,
                    assertThrows(McpProtocolException.class, () -> tasks.cancel("op-1")).code());
            assertEquals(0, operation.cancelCount());
        }
    }

    @Nested
    class Construction {

        @Test
        void followsNoTasksWhenNoOperationKindIsReachable() {
            assertSame(McpTaskProvider.NONE, OperationTasks.of(registry, kind -> false));
        }

        @Test
        void followsTasksWhenAnyOperationKindIsReachable() {
            assertInstanceOf(OperationTasks.class,
                    OperationTasks.of(registry, kind -> kind == OperationKind.JVM_AUTO_ANALYSIS));
        }
    }
}
