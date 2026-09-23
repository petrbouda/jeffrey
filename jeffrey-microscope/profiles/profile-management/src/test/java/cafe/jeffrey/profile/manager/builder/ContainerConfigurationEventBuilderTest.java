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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.manager.model.container.ContainerConfigurationData;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContainerConfigurationEventBuilder")
class ContainerConfigurationEventBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode createContainerFields() {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("containerType", "cgroupv2");
        fields.put("cpuSlicePeriod", 100000);
        fields.put("cpuQuota", 200000);
        fields.put("cpuShares", 1024);
        fields.put("effectiveCpuCount", 4);
        fields.put("memorySoftLimit", -1);
        fields.put("memoryLimit", 4294967296L);
        fields.put("swapMemoryLimit", 0);
        fields.put("hostTotalMemory", 17179869184L);
        return fields;
    }

    private static GenericRecord createRecord(Type type, ObjectNode fields) {
        return new GenericRecord(
                type,
                "Container Configuration",
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ZERO,
                Duration.ZERO,
                null,
                null,
                1,
                0,
                fields);
    }

    @Nested
    @DisplayName("SingleContainerEvent")
    class SingleContainerEvent {

        @Test
        @DisplayName("Single CONTAINER_CONFIGURATION record is correctly deserialized")
        void singleContainerConfigurationRecordIsDeserialized() {
            ContainerConfigurationEventBuilder builder = new ContainerConfigurationEventBuilder();

            GenericRecord record = createRecord(Type.CONTAINER_CONFIGURATION, createContainerFields());
            builder.onRecord(record);

            ContainerConfigurationData data = builder.build();

            assertNotNull(data);
            ContainerConfiguration config = data.configuration();
            assertNotNull(config);

            assertEquals("cgroupv2", config.containerType());
            assertEquals(100000L, config.cpuSlicePeriod());
            assertEquals(200000L, config.cpuQuota());
            assertEquals(1024L, config.cpuShares());
            assertEquals(4L, config.effectiveCpuCount());
            assertEquals(-1L, config.memorySoftLimit());
            assertEquals(4294967296L, config.memoryLimit());
            assertEquals(0L, config.swapMemoryLimit());
            assertEquals(17179869184L, config.hostTotalMemory());
        }
    }

    @Nested
    @DisplayName("MissingContainerEvent")
    class MissingContainerEvent {

        @Test
        @DisplayName("Building without any records yields null configuration")
        void buildWithoutRecordsYieldsNullConfiguration() {
            ContainerConfigurationEventBuilder builder = new ContainerConfigurationEventBuilder();

            ContainerConfigurationData data = builder.build();

            assertNotNull(data);
            assertNull(data.configuration());
        }
    }

    @Nested
    @DisplayName("NonContainerEventIgnored")
    class NonContainerEventIgnored {

        @Test
        @DisplayName("Record with a non-container type is ignored")
        void nonContainerTypeRecordIsIgnored() {
            ContainerConfigurationEventBuilder builder = new ContainerConfigurationEventBuilder();

            GenericRecord record = createRecord(Type.EXECUTION_SAMPLE, createContainerFields());
            builder.onRecord(record);

            ContainerConfigurationData data = builder.build();

            assertNotNull(data);
            assertNull(data.configuration());
        }
    }
}
