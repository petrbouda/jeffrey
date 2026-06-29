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

package cafe.jeffrey.timeseries;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeseriesDownsamplerTest {

    private static SingleSerie serie(String name, int seconds) {
        List<List<Long>> data = new ArrayList<>();
        for (long second = 0; second < seconds; second++) {
            List<Long> point = new ArrayList<>(2);
            point.add(second);
            point.add(1L);
            data.add(point);
        }
        return new SingleSerie(name, data);
    }

    private static long totalValue(SingleSerie serie) {
        return serie.data().stream().mapToLong(point -> point.get(1)).sum();
    }

    private static SingleSerie byName(TimeseriesData data, String name) {
        return data.series().stream()
                .filter(serie -> serie.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class SingleSerieInput {

        @Test
        void boundsThePointCount() {
            TimeseriesData input = new TimeseriesData(serie("Samples", 10_000));

            TimeseriesData result = TimeseriesDownsampler.downsample(input, 1_000);

            assertEquals(1, result.series().size());
            assertTrue(byName(result, "Samples").data().size() <= 1_000);
        }

        @Test
        void sumPreservesTotalValue() {
            TimeseriesData input = new TimeseriesData(serie("Samples", 10_000));

            TimeseriesData result = TimeseriesDownsampler.downsample(input, 1_000);

            assertEquals(10_000L, totalValue(byName(result, "Samples")));
        }

        @Test
        void aggregatesSpikeMassIntoItsBucket() {
            List<List<Long>> data = new ArrayList<>();
            for (long second = 0; second < 1_000; second++) {
                List<Long> point = new ArrayList<>(2);
                point.add(second);
                point.add(second == 500 ? 400L : 1L);
                data.add(point);
            }
            TimeseriesData input = new TimeseriesData(new SingleSerie("Samples", data));

            TimeseriesData result = TimeseriesDownsampler.downsample(input, 100);

            // Total mass (999 ones + one 400) is preserved by SUM.
            assertEquals(999L + 400L, totalValue(byName(result, "Samples")));
        }

        @Test
        void smallInputIsReturnedUnchanged() {
            TimeseriesData input = new TimeseriesData(serie("Samples", 50));

            TimeseriesData result = TimeseriesDownsampler.downsample(input, 1_000);

            assertEquals(1, result.series().size());
            assertEquals(50, byName(result, "Samples").data().size());
        }
    }

    @Nested
    class MultiSerieInput {

        @Test
        void reducesEachSerieKeepingSeriesCount() {
            TimeseriesData input = new TimeseriesData(
                    serie("Samples", 5_000),
                    serie("Matched Samples", 5_000));

            TimeseriesData result = TimeseriesDownsampler.downsample(input, 500);

            assertEquals(2, result.series().size());
            assertTrue(byName(result, "Samples").data().size() <= 500);
            assertTrue(byName(result, "Matched Samples").data().size() <= 500);
        }
    }
}
