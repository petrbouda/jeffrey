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

package pbouda.jeffrey.profile.manager.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import pbouda.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.model.Type;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CPULoadBuilder")
class CPULoadBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-01-01T00:00:00Z");

    private static GenericRecord createRecord(double systemLoad, double userLoad, int index) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("system", systemLoad);
        fields.put("user", userLoad);

        JfrThread thread = mock(JfrThread.class);
        when(thread.osThreadId()).thenReturn((long) index);
        when(thread.javaThreadId()).thenReturn((long) index);
        when(thread.name()).thenReturn("thread-" + index);

        Instant timestamp = BASE_TIMESTAMP.plusSeconds(index);
        return new GenericRecord(
                Type.THREAD_CPU_LOAD,
                "Thread CPU Load",
                timestamp,
                Duration.ofSeconds(index),
                Duration.ZERO,
                thread,
                null,
                1,
                0,
                fields);
    }

    @Nested
    @DisplayName("TopNByUserCpu")
    class TopNByUserCpu {

        @Test
        @DisplayName("Keeps only the top N records sorted descending by user CPU load")
        void keepsTopNByUserCpuLoad() {
            CPULoadBuilder builder = new CPULoadBuilder(3);

            double[] userLoads = {0.1, 0.5, 0.3, 0.9, 0.7};
            for (int i = 0; i < userLoads.length; i++) {
                builder.onRecord(createRecord(0.0, userLoads[i], i));
            }

            ThreadCpuLoads result = builder.build();
            List<ThreadWithCpuLoad> userList = result.user();

            assertEquals(3, userList.size());
            assertEquals(0, BigDecimal.valueOf(0.9).compareTo(userList.get(0).cpuLoad()));
            assertEquals(0, BigDecimal.valueOf(0.7).compareTo(userList.get(1).cpuLoad()));
            assertEquals(0, BigDecimal.valueOf(0.5).compareTo(userList.get(2).cpuLoad()));
        }
    }

    @Nested
    @DisplayName("TopNBySystemCpu")
    class TopNBySystemCpu {

        @Test
        @DisplayName("Keeps only the top N records sorted descending by system CPU load")
        void keepsTopNBySystemCpuLoad() {
            CPULoadBuilder builder = new CPULoadBuilder(3);

            double[] systemLoads = {0.1, 0.5, 0.3, 0.9, 0.7};
            for (int i = 0; i < systemLoads.length; i++) {
                builder.onRecord(createRecord(systemLoads[i], 0.0, i));
            }

            ThreadCpuLoads result = builder.build();
            List<ThreadWithCpuLoad> systemList = result.system();

            assertEquals(3, systemList.size());
            assertEquals(0, BigDecimal.valueOf(0.9).compareTo(systemList.get(0).cpuLoad()));
            assertEquals(0, BigDecimal.valueOf(0.7).compareTo(systemList.get(1).cpuLoad()));
            assertEquals(0, BigDecimal.valueOf(0.5).compareTo(systemList.get(2).cpuLoad()));
        }
    }

    @Nested
    @DisplayName("EmptyRecords")
    class EmptyRecords {

        @Test
        @DisplayName("Building without any records produces empty lists")
        void buildWithoutRecordsProducesEmptyLists() {
            CPULoadBuilder builder = new CPULoadBuilder(5);

            ThreadCpuLoads result = builder.build();

            assertNotNull(result);
            assertTrue(result.user().isEmpty());
            assertTrue(result.system().isEmpty());
        }
    }

    @Nested
    @DisplayName("LimitZero")
    class LimitZero {

        @Test
        @DisplayName("Limit of zero with no records produces empty lists")
        void limitZeroNoRecordsProducesEmptyLists() {
            CPULoadBuilder builder = new CPULoadBuilder(0);

            ThreadCpuLoads result = builder.build();

            assertNotNull(result);
            assertTrue(result.user().isEmpty());
            assertTrue(result.system().isEmpty());
        }

        @Test
        @DisplayName("Limit of zero throws NullPointerException when records are fed")
        void limitZeroWithRecordsThrowsNpe() {
            CPULoadBuilder builder = new CPULoadBuilder(0);

            GenericRecord record = createRecord(0.5, 0.3, 0);
            assertThrows(NullPointerException.class, () -> builder.onRecord(record));
        }
    }

    @Nested
    @DisplayName("FewerRecordsThanLimit")
    class FewerRecordsThanLimit {

        @Test
        @DisplayName("When fewer records than limit, all records are included")
        void fewerRecordsThanLimitIncludesAll() {
            CPULoadBuilder builder = new CPULoadBuilder(5);

            builder.onRecord(createRecord(0.4, 0.6, 0));
            builder.onRecord(createRecord(0.2, 0.8, 1));

            ThreadCpuLoads result = builder.build();

            assertEquals(2, result.user().size());
            assertEquals(2, result.system().size());
        }
    }
}
