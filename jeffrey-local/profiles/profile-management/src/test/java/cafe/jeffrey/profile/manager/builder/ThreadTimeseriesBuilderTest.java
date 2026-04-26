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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.model.GenericRecord;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
