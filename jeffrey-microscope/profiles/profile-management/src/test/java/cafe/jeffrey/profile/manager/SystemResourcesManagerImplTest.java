/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.model.system.SystemProcessInfo;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemResourcesManagerImpl")
class SystemResourcesManagerImplTest {

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private SystemResourcesManagerImpl manager() {
        return new SystemResourcesManagerImpl(PROFILE_INFO, eventStreamRepository);
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

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode cpuFields(double jvmUser, double jvmSystem, double machineTotal) {
        ObjectNode node = Json.createObject();
        node.put("jvmUser", jvmUser);
        node.put("jvmSystem", jvmSystem);
        node.put("machineTotal", machineTotal);
        return node;
    }

    @Nested
    @DisplayName("cpuTimeline()")
    class CpuTimeline {

        @Test
        @DisplayName("Converts fractions to basis points across three series")
        void basisPoints() {
            stubStreaming(
                    record(Type.CPU_LOAD, 1, cpuFields(0.01, 0.005, 0.3271)),
                    record(Type.CPU_LOAD, 2, cpuFields(0.02, 0.010, 0.5)));

            TimeseriesData timeline = manager().cpuTimeline();

            assertEquals(3, timeline.series().size());
            assertEquals("Machine Total", timeline.series().get(0).name());
            assertEquals(3271, valueAt(timeline.series().get(0), 1));
            assertEquals(5000, valueAt(timeline.series().get(0), 2));
            assertEquals(100, valueAt(timeline.series().get(1), 1));
            assertEquals(50, valueAt(timeline.series().get(2), 1));
        }
    }

    @Nested
    @DisplayName("contextSwitchTimeline()")
    class ContextSwitches {

        @Test
        @DisplayName("Parses string-typed switchRate values")
        void parsesStringRates() {
            ObjectNode fields = Json.createObject();
            // float-typed JFR fields without unit annotations arrive as numeric strings
            fields.put("switchRate", "23076.355");
            stubStreaming(record(Type.THREAD_CONTEXT_SWITCH_RATE, 1, fields));

            TimeseriesData timeline = manager().contextSwitchTimeline();

            assertEquals(23076, valueAt(timeline.series().getFirst(), 1));
        }
    }

    @Nested
    @DisplayName("networkTimeline()")
    class NetworkTimeline {

        @Test
        @DisplayName("Pushes the interface filter into the query and converts bits to bytes")
        void filtersAndConverts() {
            ObjectNode fields = Json.createObject();
            fields.put("networkInterface", "eth0");
            fields.put("readRate", 8_000L);
            fields.put("writeRate", 1_600L);
            stubStreaming(record(Type.NETWORK_UTILIZATION, 1, fields));

            TimeseriesData timeline = manager().networkTimeline("eth0");

            assertEquals(1_000, valueAt(timeline.series().get(0), 1));
            assertEquals(200, valueAt(timeline.series().get(1), 1));

            ArgumentCaptor<EventQueryConfigurer> captor = ArgumentCaptor.forClass(EventQueryConfigurer.class);
            verify(eventStreamRepository).genericStreaming(captor.capture(), any());
            assertEquals("eth0", captor.getValue().jsonFieldFilter().value());
        }
    }

    @Nested
    @DisplayName("processes()")
    class Processes {

        @Test
        @DisplayName("Dedupes by pid keeping the last snapshot, sorted by command line")
        void dedupesByPid() {
            stubStreaming(
                    record(Type.SYSTEM_PROCESS, 0, processFields("100", "zebra-daemon")),
                    record(Type.SYSTEM_PROCESS, 0, processFields("200", "apache2 ")),
                    record(Type.SYSTEM_PROCESS, 1, processFields("100", "zebra-daemon-v2")));

            List<SystemProcessInfo> processes = manager().processes();

            assertEquals(2, processes.size());
            assertEquals("apache2", processes.getFirst().commandLine());
            assertEquals("zebra-daemon-v2", processes.get(1).commandLine());
        }

        private ObjectNode processFields(String pid, String commandLine) {
            ObjectNode node = Json.createObject();
            node.put("pid", pid);
            node.put("commandLine", commandLine);
            return node;
        }
    }

    private static long valueAt(SingleSerie serie, long second) {
        return serie.data().stream()
                .filter(point -> point.get(0) == second)
                .map(point -> point.get(1))
                .findFirst()
                .orElseThrow();
    }
}
