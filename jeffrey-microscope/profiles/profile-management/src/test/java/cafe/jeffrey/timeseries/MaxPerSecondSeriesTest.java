/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.timeseries;

import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaxPerSecondSeriesTest {

    /** Seconds 0 to 3 of the recording. */
    private static final RelativeTimeRange FOUR_SECONDS = new RelativeTimeRange(0, 3_000);

    private static MaxPerSecondSeries series() {
        MaxPerSecondSeries series = new MaxPerSecondSeries("Heap", FOUR_SECONDS);
        series.record(1, 30);
        series.record(1, 50);
        series.record(1, 40);
        series.record(3, 10);
        return series;
    }

    @Test
    void keepsTheLargestValueOfEachSecondAndZeroWhereNothingWasRecorded() {
        SingleSerie serie = series().build();

        assertEquals("Heap", serie.name());
        assertEquals(List.of(List.of(0L, 0L), List.of(1L, 50L), List.of(2L, 0L), List.of(3L, 10L)), serie.data());
    }

    @Test
    void carriesTheLastValueAcrossSecondsWithNothingRecorded() {
        SingleSerie serie = series().buildCarryingForward();

        assertEquals(List.of(List.of(0L, 0L), List.of(1L, 50L), List.of(2L, 50L), List.of(3L, 10L)), serie.data());
    }
}
