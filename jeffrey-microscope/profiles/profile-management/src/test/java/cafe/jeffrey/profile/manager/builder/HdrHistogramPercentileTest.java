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

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HdrHistogram getValueAtPercentile API usage")
class HdrHistogramPercentileTest {

    private Histogram histogram;

    @BeforeEach
    void setUp() {
        histogram = new Histogram(3);
        for (long i = 1; i <= 100; i++) {
            histogram.recordValue(i);
        }
    }

    @Nested
    @DisplayName("Correct usage: percentile on 0-100 scale")
    class CorrectScale {

        @Test
        @DisplayName("99th percentile (99.0) returns approximately 99")
        void p99ReturnsExpectedValue() {
            long value = histogram.getValueAtPercentile(99.0);
            assertTrue(
                    Math.abs(value - 99) <= 1,
                    "Expected ~99 for 99th percentile, but got: " + value
            );
        }

        @Test
        @DisplayName("95th percentile (95.0) returns approximately 95")
        void p95ReturnsExpectedValue() {
            long value = histogram.getValueAtPercentile(95.0);
            assertTrue(
                    Math.abs(value - 95) <= 1,
                    "Expected ~95 for 95th percentile, but got: " + value
            );
        }
    }

    @Nested
    @DisplayName("Bug: passing 0-1 scale instead of 0-100 returns near-minimum values")
    class IncorrectScaleBug {

        @Test
        @DisplayName("0.99 is treated as ~0th percentile and returns approximately 1")
        void passingZeroPointNineNineReturnNearMinimum() {
            long value = histogram.getValueAtPercentile(0.99);
            assertTrue(
                    Math.abs(value - 1) <= 1,
                    "Expected ~1 for 0.99 (essentially 0th percentile), but got: " + value
            );
        }

        @Test
        @DisplayName("0.95 is treated as ~0th percentile and returns approximately 1")
        void passingZeroPointNineFiveReturnNearMinimum() {
            long value = histogram.getValueAtPercentile(0.95);
            assertTrue(
                    Math.abs(value - 1) <= 1,
                    "Expected ~1 for 0.95 (essentially 0th percentile), but got: " + value
            );
        }
    }
}
