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

package pbouda.jeffrey.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DurationUtilsTest {

    @Nested
    class Parse {

        @Test
        void nullThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse(null)
            );
            assertEquals("Duration string cannot be null or empty", exception.getMessage());
        }

        @Test
        void emptyStringThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse("")
            );
            assertEquals("Duration string cannot be null or empty", exception.getMessage());
        }

        @Test
        void whitespaceOnlyThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse("   ")
            );
            assertEquals("Duration string cannot be null or empty", exception.getMessage());
        }

        @ParameterizedTest
        @MethodSource("validDurationsWithSpaces")
        void parseWithSpaces(String input, Duration expected) {
            assertEquals(expected, DurationUtils.parse(input));
        }

        @ParameterizedTest
        @MethodSource("validDurationsWithoutSpaces")
        void parseWithoutSpaces(String input, Duration expected) {
            assertEquals(expected, DurationUtils.parse(input));
        }

        @Test
        void invalidNumberWithSpaceThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse("abc ms")
            );
            assertTrue(exception.getMessage().contains("Invalid number format in duration"));
        }

        @Test
        void invalidNumberWithoutSpaceThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse("abcms")
            );
            assertTrue(exception.getMessage().contains("Invalid duration format"));
        }

        @Test
        void invalidUnitWithSpaceThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse("100 xs")
            );
            assertTrue(exception.getMessage().contains("Unknown time unit"));
        }

        @Test
        void invalidUnitWithoutSpaceThrowsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DurationUtils.parse("100xs")
            );
            assertTrue(exception.getMessage().contains("Unknown time unit"));
        }

        @Test
        void parseNanoseconds() {
            assertEquals(Duration.ofNanos(500), DurationUtils.parse("500ns"));
            assertEquals(Duration.ofNanos(500), DurationUtils.parse("500 ns"));
        }

        @Test
        void parseMicroseconds() {
            assertEquals(Duration.ofNanos(500_000), DurationUtils.parse("500us"));
            assertEquals(Duration.ofNanos(500_000), DurationUtils.parse("500μs"));
            assertEquals(Duration.ofNanos(500_000), DurationUtils.parse("500 us"));
            assertEquals(Duration.ofNanos(500_000), DurationUtils.parse("500 μs"));
        }

        @Test
        void parseMilliseconds() {
            assertEquals(Duration.ofMillis(500), DurationUtils.parse("500ms"));
            assertEquals(Duration.ofMillis(500), DurationUtils.parse("500 ms"));
        }

        @Test
        void parseSeconds() {
            assertEquals(Duration.ofSeconds(30), DurationUtils.parse("30s"));
            assertEquals(Duration.ofSeconds(30), DurationUtils.parse("30 s"));
        }

        @Test
        void parseMinutes() {
            assertEquals(Duration.ofMinutes(15), DurationUtils.parse("15m"));
            assertEquals(Duration.ofMinutes(15), DurationUtils.parse("15 m"));
        }

        @Test
        void parseHours() {
            assertEquals(Duration.ofHours(2), DurationUtils.parse("2h"));
            assertEquals(Duration.ofHours(2), DurationUtils.parse("2 h"));
        }

        @Test
        void parseDays() {
            assertEquals(Duration.ofDays(7), DurationUtils.parse("7d"));
            assertEquals(Duration.ofDays(7), DurationUtils.parse("7 d"));
        }

        @Test
        void parseZeroValue() {
            assertEquals(Duration.ZERO, DurationUtils.parse("0ms"));
            assertEquals(Duration.ZERO, DurationUtils.parse("0 s"));
        }

        @Test
        void parseLargeValues() {
            assertEquals(Duration.ofDays(365), DurationUtils.parse("365d"));
            assertEquals(Duration.ofMillis(1_000_000), DurationUtils.parse("1000000ms"));
        }

        private static Stream<Arguments> validDurationsWithSpaces() {
            return Stream.of(
                    Arguments.of("1000 ns", Duration.ofNanos(1000)),
                    Arguments.of("1000 μs", Duration.ofNanos(1_000_000)),
                    Arguments.of("1000 us", Duration.ofNanos(1_000_000)),
                    Arguments.of("1000 ms", Duration.ofMillis(1000)),
                    Arguments.of("60 s", Duration.ofSeconds(60)),
                    Arguments.of("5 m", Duration.ofMinutes(5)),
                    Arguments.of("2 h", Duration.ofHours(2)),
                    Arguments.of("1 d", Duration.ofDays(1)),
                    Arguments.of("0 ms", Duration.ofMillis(0))
            );
        }

        private static Stream<Arguments> validDurationsWithoutSpaces() {
            return Stream.of(
                    Arguments.of("1000ns", Duration.ofNanos(1000)),
                    Arguments.of("1000μs", Duration.ofNanos(1_000_000)),
                    Arguments.of("1000us", Duration.ofNanos(1_000_000)),
                    Arguments.of("1000ms", Duration.ofMillis(1000)),
                    Arguments.of("60s", Duration.ofSeconds(60)),
                    Arguments.of("5m", Duration.ofMinutes(5)),
                    Arguments.of("2h", Duration.ofHours(2)),
                    Arguments.of("1d", Duration.ofDays(1)),
                    Arguments.of("0ms", Duration.ofMillis(0))
            );
        }
    }

    @Nested
    class ParseOrDefault {

        @Test
        void validInputReturnsParseValue() {
            Duration defaultDuration = Duration.ofSeconds(30);
            assertEquals(Duration.ofMillis(1000), DurationUtils.parseOrDefault("1000 ms", defaultDuration));
        }

        @Test
        void validInputWithoutSpaceReturnsParseValue() {
            Duration defaultDuration = Duration.ofSeconds(30);
            assertEquals(Duration.ofMillis(1000), DurationUtils.parseOrDefault("1000ms", defaultDuration));
        }

        @Test
        void invalidInputReturnsDefault() {
            Duration defaultDuration = Duration.ofSeconds(30);
            assertEquals(defaultDuration, DurationUtils.parseOrDefault("invalid", defaultDuration));
        }

        @Test
        void nullInputReturnsDefault() {
            Duration defaultDuration = Duration.ofSeconds(30);
            assertEquals(defaultDuration, DurationUtils.parseOrDefault(null, defaultDuration));
        }

        @Test
        void emptyInputReturnsDefault() {
            Duration defaultDuration = Duration.ofMinutes(5);
            assertEquals(defaultDuration, DurationUtils.parseOrDefault("", defaultDuration));
        }
    }

    @Nested
    class Format {

        @Test
        void formatDays() {
            Duration d = Duration.ofDays(2);
            assertTrue(DurationUtils.format(d).contains("2d"));
        }

        @Test
        void formatHours() {
            Duration d = Duration.ofHours(3);
            assertTrue(DurationUtils.format(d).contains("3h"));
        }

        @Test
        void formatMinutes() {
            Duration d = Duration.ofMinutes(45);
            assertTrue(DurationUtils.format(d).contains("45m"));
        }

        @Test
        void formatSeconds() {
            Duration d = Duration.ofSeconds(30);
            assertTrue(DurationUtils.format(d).contains("30s"));
        }

        @Test
        void formatMilliseconds() {
            Duration d = Duration.ofMillis(500);
            assertTrue(DurationUtils.format(d).contains("500ms"));
        }

        @Test
        void formatMicroseconds() {
            Duration d = Duration.ofNanos(5000);  // 5 microseconds
            assertTrue(DurationUtils.format(d).contains("5µs"));
        }

        @Test
        void formatNanoseconds() {
            Duration d = Duration.ofNanos(100);
            assertTrue(DurationUtils.format(d).contains("100ns"));
        }

        @Test
        void formatComplexDuration() {
            // 1 day, 2 hours, 30 minutes, 45 seconds
            Duration d = Duration.ofDays(1)
                    .plusHours(2)
                    .plusMinutes(30)
                    .plusSeconds(45);
            String formatted = DurationUtils.format(d);
            assertTrue(formatted.contains("1d"));
            assertTrue(formatted.contains("2h"));
            assertTrue(formatted.contains("30m"));
            assertTrue(formatted.contains("45s"));
        }

        @Test
        void formatZeroDuration() {
            Duration d = Duration.ZERO;
            assertEquals("", DurationUtils.format(d));
        }

        @Test
        void formatOnlyNonZeroParts() {
            Duration d = Duration.ofHours(5).plusSeconds(10);
            String formatted = DurationUtils.format(d);
            assertTrue(formatted.contains("5h"));
            assertTrue(formatted.contains("10s"));
            assertFalse(formatted.contains("d"));
            assertFalse(formatted.contains("m "));  // space after m to not match "ms"
        }
    }

    @Nested
    class FormatNanos {

        @Test
        void formatNanosecondValue() {
            String result = DurationUtils.formatNanos(500);
            assertTrue(result.contains("500ns"));
        }

        @Test
        void formatMicrosecondValue() {
            String result = DurationUtils.formatNanos(5_000);  // 5 microseconds
            assertTrue(result.contains("5µs"));
        }

        @Test
        void formatMillisecondValue() {
            String result = DurationUtils.formatNanos(5_000_000);  // 5 milliseconds
            assertTrue(result.contains("5ms"));
        }

        @Test
        void formatSecondValue() {
            String result = DurationUtils.formatNanos(1_000_000_000L);  // 1 second
            assertTrue(result.contains("1s"));
        }

        @Test
        void formatLargeValue() {
            // 1 minute in nanos
            long oneMinuteNanos = 60L * 1_000_000_000L;
            String result = DurationUtils.formatNanos(oneMinuteNanos);
            assertTrue(result.contains("1m"));
        }
    }

    @Nested
    class Format2Parts {

        @Test
        void returnsOnlyTwoPartsForComplexDuration() {
            Duration d = Duration.ofDays(1)
                    .plusHours(2)
                    .plusMinutes(30)
                    .plusSeconds(45);
            String result = DurationUtils.format2parts(d);
            String[] parts = result.trim().split(" ");
            assertEquals(2, parts.length);
        }

        @Test
        void returnsMostSignificantParts() {
            Duration d = Duration.ofDays(2).plusHours(5).plusMinutes(30);
            String result = DurationUtils.format2parts(d);
            assertTrue(result.contains("2d"));
            assertTrue(result.contains("5h"));
            assertFalse(result.contains("30m"));
        }

        @Test
        void returnsSinglePartIfOnlyOne() {
            Duration d = Duration.ofHours(3);
            String result = DurationUtils.format2parts(d);
            assertTrue(result.contains("3h"));
        }

        @Test
        void returnsTwoPartsIfExactlyTwo() {
            Duration d = Duration.ofMinutes(10).plusSeconds(30);
            String result = DurationUtils.format2parts(d);
            assertTrue(result.contains("10m"));
            assertTrue(result.contains("30s"));
        }

        @Test
        void formatNanos2UnitsReturnsMaxTwoParts() {
            // 1 hour, 30 minutes, 45 seconds in nanos
            long nanos = Duration.ofHours(1)
                    .plusMinutes(30)
                    .plusSeconds(45)
                    .toNanos();
            String result = DurationUtils.formatNanos2Units(nanos);
            String[] parts = result.trim().split(" ");
            assertTrue(parts.length <= 2);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void parseThenFormatRoundTrip() {
            Duration parsed = DurationUtils.parse("500ms");
            String formatted = DurationUtils.format(parsed);
            assertTrue(formatted.contains("500ms"));
        }

        @Test
        void parseWithLeadingWhitespace() {
            assertEquals(Duration.ofMillis(100), DurationUtils.parse("  100ms"));
        }

        @Test
        void parseWithTrailingWhitespace() {
            assertEquals(Duration.ofMillis(100), DurationUtils.parse("100ms  "));
        }

        @Test
        void parseWithMultipleSpacesBetweenValueAndUnit() {
            assertEquals(Duration.ofMillis(100), DurationUtils.parse("100   ms"));
        }

        @ParameterizedTest
        @CsvSource({
                "1ns, 1",
                "1000ns, 1000",
                "1us, 1000",
                "1ms, 1000000",
                "1s, 1000000000"
        })
        void parseToNanosConversion(String input, long expectedNanos) {
            Duration parsed = DurationUtils.parse(input);
            assertEquals(expectedNanos, parsed.toNanos());
        }
    }
}
