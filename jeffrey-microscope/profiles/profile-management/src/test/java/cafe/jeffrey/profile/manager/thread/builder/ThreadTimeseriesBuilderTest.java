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

package cafe.jeffrey.profile.manager.thread.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadTimeseriesBuilder")
class ThreadTimeseriesBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-01-01T00:00:00Z");

    private static GenericRecord createRecord(int secondsFromStart, long activeCount) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("activeCount", activeCount);

        return new GenericRecord(
                Type.JAVA_THREAD_STATISTICS,
                "Java Thread Statistics",
                BASE_TIMESTAMP.plusSeconds(secondsFromStart),
                Duration.ofSeconds(secondsFromStart),
                Duration.ZERO,
                null,
                null,
                1,
                0,
                fields);
    }

    @Nested
    @DisplayName("ActiveThreadCountTracked")
    class ActiveThreadCountTracked {

        @Test
        @DisplayName("Records at different seconds are tracked and serie is named Active Threads")
        void recordsAtDifferentSecondsAreTracked() {
            RelativeTimeRange timeRange = new RelativeTimeRange(0, 10_000);
            ThreadTimeseriesBuilder builder = new ThreadTimeseriesBuilder(timeRange);

            builder.onRecord(createRecord(1, 10));
            builder.onRecord(createRecord(3, 20));
            builder.onRecord(createRecord(5, 15));

            SingleSerie serie = builder.build();

            assertEquals("Active Threads", serie.name());
            assertFalse(serie.data().isEmpty());
        }
    }

    @Nested
    @DisplayName("MaxValuePerSecond")
    class MaxValuePerSecond {

        @Test
        @DisplayName("When multiple records fall in the same second the maximum activeCount is kept")
        void maximumActiveCountIsKeptPerSecond() {
            RelativeTimeRange timeRange = new RelativeTimeRange(0, 10_000);
            ThreadTimeseriesBuilder builder = new ThreadTimeseriesBuilder(timeRange);

            builder.onRecord(createRecord(2, 10));
            builder.onRecord(createRecord(2, 20));

            SingleSerie serie = builder.build();

            List<List<Long>> data = serie.data();
            long valueAtSecondTwo = data.stream()
                    .filter(entry -> entry.get(0) == 2L)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No entry found for second 2"))
                    .get(1);

            assertEquals(20L, valueAtSecondTwo);
        }
    }

    @Nested
    @DisplayName("NoEvents")
    class NoEvents {

        @Test
        @DisplayName("Building without any records returns pre-filled zeros and correct serie name")
        void buildWithoutRecordsReturnsPrefilled() {
            RelativeTimeRange timeRange = new RelativeTimeRange(0, 10_000);
            ThreadTimeseriesBuilder builder = new ThreadTimeseriesBuilder(timeRange);

            SingleSerie serie = builder.build();

            assertEquals("Active Threads", serie.name());
            assertFalse(serie.data().isEmpty());
        }
    }
}
