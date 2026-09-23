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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VmOperationManagerImpl")
class VmOperationManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private VmOperationManagerImpl manager() {
        return new VmOperationManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubStreaming(GenericRecord... records) {
        when(eventStreamRepository.genericStreaming(any(), any())).thenAnswer(invocation -> {
            RecordBuilder<GenericRecord, Object> builder = invocation.getArgument(1);
            for (GenericRecord record : records) {
                builder.onRecord(record);
            }
            return builder.build();
        });
    }

    private static GenericRecord record(Type type, long secondsFromStart, Duration duration, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), duration,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode vmOperationFields(String operation, boolean safepoint, boolean blocking) {
        ObjectNode node = Json.createObject();
        node.put("operation", operation);
        node.put("safepoint", safepoint);
        node.put("blocking", blocking);
        return node;
    }

    @Nested
    @DisplayName("vmOperations()")
    class VmOperations {

        @Test
        @DisplayName("Returns empty without streaming when the event is absent")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.EXECUTE_VM_OPERATION)).thenReturn(false);

            assertTrue(manager().vmOperations().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Groups by operation ordered by total duration")
        void groupsByOperation() {
            when(eventRepository.containsEventType(Type.EXECUTE_VM_OPERATION)).thenReturn(true);
            stubStreaming(
                    record(Type.EXECUTE_VM_OPERATION, 1, Duration.ofMillis(20),
                            vmOperationFields("G1CollectForAllocation", true, true)),
                    record(Type.EXECUTE_VM_OPERATION, 2, Duration.ofMillis(5),
                            vmOperationFields("G1CollectForAllocation", true, true)),
                    record(Type.EXECUTE_VM_OPERATION, 3, Duration.ofMillis(1),
                            vmOperationFields("ThreadDump", true, false)));

            List<VmOperationStat> operations = manager().vmOperations();

            assertEquals(2, operations.size());
            VmOperationStat g1 = operations.getFirst();
            assertEquals("G1CollectForAllocation", g1.operation());
            assertEquals(2, g1.count());
            assertEquals(Duration.ofMillis(25).toNanos(), g1.totalNanos());
            assertEquals(Duration.ofMillis(20).toNanos(), g1.maxNanos());
            assertTrue(g1.safepoint());
        }
    }

    @Nested
    @DisplayName("pausesTimeline()")
    class PausesTimeline {

        @Test
        @DisplayName("Sums only safepoint operations per second")
        void sumsSafepointPauses() {
            stubStreaming(
                    record(Type.EXECUTE_VM_OPERATION, 2, Duration.ofMillis(10),
                            vmOperationFields("G1CollectForAllocation", true, true)),
                    record(Type.EXECUTE_VM_OPERATION, 2, Duration.ofMillis(5),
                            vmOperationFields("ThreadDump", true, true)),
                    record(Type.EXECUTE_VM_OPERATION, 2, Duration.ofMillis(100),
                            vmOperationFields("NonSafepointOp", false, true)));

            TimeseriesData timeline = manager().pausesTimeline();

            SingleSerie serie = timeline.series().getFirst();
            long valueAtSecond2 = serie.data().stream()
                    .filter(point -> point.get(0) == 2)
                    .map(point -> point.get(1))
                    .findFirst()
                    .orElseThrow();
            assertEquals(Duration.ofMillis(15).toNanos(), valueAtSecond2);
        }
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("All has-flags false when no VM-operation events exist")
        void allFlagsFalseWhenEmpty() {
            when(eventRepository.containsEventType(any())).thenReturn(false);

            var vm = manager().overview();
            assertEquals(0, vm.vmOperationCount());
            assertFalse(vm.hasVmOperations());
            assertFalse(vm.hasTimeToSafepoint());
            assertFalse(vm.hasSafepointOffenders());
        }
    }
}
