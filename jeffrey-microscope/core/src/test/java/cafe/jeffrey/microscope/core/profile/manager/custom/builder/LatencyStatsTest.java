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

package cafe.jeffrey.microscope.core.profile.manager.custom.builder;

import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyStatsTest {

    @Test
    void answersZeroForEveryFigureWhenNothingWasRecorded() {
        LatencyStats empty = new LatencyStats();

        assertEquals(0, empty.count());
        assertEquals(0, empty.max());
        assertEquals(0, empty.p99());
        assertEquals(0, empty.p95());
    }

    /** The whole is a percentile of the merged distribution, not the largest of the parts' percentiles. */
    @Test
    void mergesPartsIntoOneDistribution() {
        LatencyStats fast = new LatencyStats();
        LongStream.rangeClosed(1, 99).forEach(fast::record);
        LatencyStats slow = new LatencyStats();
        slow.record(1_000);

        LatencyStats whole = new LatencyStats();
        whole.add(fast);
        whole.add(slow);

        assertEquals(100, whole.count());
        assertEquals(99, whole.p99());
        assertEquals(95, whole.p95());
        assertEquals(1_000, whole.max());
    }
}
