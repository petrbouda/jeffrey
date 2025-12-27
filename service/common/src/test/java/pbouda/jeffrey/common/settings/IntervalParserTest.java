/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.common.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class IntervalParserTest {

    @Nested
    class DefaultConstant {

        @Test
        void asyncProfilerDefaultIs10Milliseconds() {
            assertEquals(Duration.ofMillis(10), IntervalParser.ASYNC_PROFILER_DEFAULT);
        }
    }

    @Nested
    class NullAndEmpty {

        @Test
        void nullReturnsZeroDuration() {
            Duration result = IntervalParser.parse(null);

            assertEquals(Duration.ZERO, result);
        }

        @Test
        void emptyStringReturnsZeroDuration() {
            Duration result = IntervalParser.parse("");

            assertEquals(Duration.ZERO, result);
        }
    }

    @Nested
    class MillisecondParsing {

        @Test
        void parseMilliseconds() {
            Duration result = IntervalParser.parse("100ms");

            assertEquals(Duration.ofMillis(100), result);
        }

        @Test
        void parseOneMillisecond() {
            Duration result = IntervalParser.parse("1ms");

            assertEquals(Duration.ofMillis(1), result);
        }

        @Test
        void parseZeroMilliseconds() {
            Duration result = IntervalParser.parse("0ms");

            assertEquals(Duration.ZERO, result);
        }

        @Test
        void parseLargeMilliseconds() {
            Duration result = IntervalParser.parse("1000000ms");

            assertEquals(Duration.ofMillis(1_000_000), result);
        }

        @ParameterizedTest
        @CsvSource({
                "10ms, 10",
                "50ms, 50",
                "100ms, 100",
                "500ms, 500",
                "1000ms, 1000"
        })
        void variousMillisecondValues(String input, long expectedMs) {
            Duration result = IntervalParser.parse(input);

            assertEquals(Duration.ofMillis(expectedMs), result);
        }
    }

    @Nested
    class SecondParsing {

        @Test
        void parseSeconds() {
            Duration result = IntervalParser.parse("5s");

            assertEquals(Duration.ofSeconds(5), result);
        }

        @Test
        void parseOneSecond() {
            Duration result = IntervalParser.parse("1s");

            assertEquals(Duration.ofSeconds(1), result);
        }

        @Test
        void parseZeroSeconds() {
            Duration result = IntervalParser.parse("0s");

            assertEquals(Duration.ZERO, result);
        }

        @Test
        void parseLargeSeconds() {
            Duration result = IntervalParser.parse("3600s");

            assertEquals(Duration.ofSeconds(3600), result);
        }

        @ParameterizedTest
        @CsvSource({
                "1s, 1",
                "10s, 10",
                "60s, 60",
                "120s, 120"
        })
        void variousSecondValues(String input, long expectedSec) {
            Duration result = IntervalParser.parse(input);

            assertEquals(Duration.ofSeconds(expectedSec), result);
        }
    }

    @Nested
    class MicrosecondParsing {

        @Test
        void parseMicroseconds() {
            Duration result = IntervalParser.parse("100us");

            assertEquals(Duration.ofNanos(100_000), result);
        }

        @Test
        void parseOneMicrosecond() {
            Duration result = IntervalParser.parse("1us");

            assertEquals(Duration.ofNanos(1000), result);
        }

        @Test
        void parseZeroMicroseconds() {
            Duration result = IntervalParser.parse("0us");

            assertEquals(Duration.ZERO, result);
        }

        @Test
        void parse1000MicrosecondsEquals1Millisecond() {
            Duration result = IntervalParser.parse("1000us");

            assertEquals(Duration.ofMillis(1), result);
        }

        @ParameterizedTest
        @CsvSource({
                "1us, 1000",
                "10us, 10000",
                "100us, 100000",
                "1000us, 1000000"
        })
        void variousMicrosecondValues(String input, long expectedNanos) {
            Duration result = IntervalParser.parse(input);

            assertEquals(Duration.ofNanos(expectedNanos), result);
        }
    }

    @Nested
    class InvalidFormats {

        @ParameterizedTest
        @ValueSource(strings = {"100", "100m", "100ns", "100min", "100h", "abc", "ms100", "s100"})
        void invalidUnitThrowsException(String input) {
            assertThrows(IllegalArgumentException.class, () -> IntervalParser.parse(input));
        }

        @Test
        void invalidNumberThrowsException() {
            assertThrows(NumberFormatException.class, () -> IntervalParser.parse("abcms"));
        }

        @Test
        void onlyUnitWithoutNumberThrowsException() {
            assertThrows(NumberFormatException.class, () -> IntervalParser.parse("ms"));
        }
    }

    @Nested
    class WhitespaceHandling {

        @Test
        void whitespaceBeforeUnitIsTrimmed() {
            Duration result = IntervalParser.parse("100 ms");

            assertEquals(Duration.ofMillis(100), result);
        }

        @Test
        void whitespaceBeforeSecondsUnitIsTrimmed() {
            Duration result = IntervalParser.parse("5 s");

            assertEquals(Duration.ofSeconds(5), result);
        }
    }

    @Nested
    class RealWorldScenarios {

        @Test
        void typicalAsyncProfilerInterval() {
            // async-profiler typically uses 10ms or similar intervals
            Duration result = IntervalParser.parse("10ms");

            assertEquals(IntervalParser.ASYNC_PROFILER_DEFAULT, result);
        }

        @Test
        void highFrequencySampling() {
            // High frequency sampling at 100us
            Duration result = IntervalParser.parse("100us");

            assertEquals(Duration.ofNanos(100_000), result);
        }

        @Test
        void lowFrequencySampling() {
            // Low frequency sampling at 1 second
            Duration result = IntervalParser.parse("1s");

            assertEquals(Duration.ofSeconds(1), result);
        }
    }
}
