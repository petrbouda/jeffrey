/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DurationUtils} class.
 */
class DurationUtilsTest {

    @Test
    void testParseWithNullOrEmptyString() {
        // Test with null
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse(null)
        );
        assertEquals("Duration string cannot be null or empty", exception.getMessage());

        // Test with empty string
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse("")
        );
        assertEquals("Duration string cannot be null or empty", exception.getMessage());

        // Test with whitespace only
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse("   ")
        );
        assertEquals("Duration string cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("validDurationsWithSpaces")
    void testParseWithSpaces(String input, Duration expected) {
        assertEquals(expected, DurationUtils.parse(input));
    }

    @ParameterizedTest
    @MethodSource("validDurationsWithoutSpaces")
    void testParseWithoutSpaces(String input, Duration expected) {
        assertEquals(expected, DurationUtils.parse(input));
    }

    @Test
    void testParseWithInvalidFormat() {
        // Invalid format with space
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse("abc ms")
        );
        assertTrue(exception.getMessage().contains("Invalid number format in duration"));

        // Invalid format without space
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse("abcms")
        );
        assertTrue(exception.getMessage().contains("Invalid duration format"));

        // Invalid unit with space
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse("100 xs")
        );
        assertTrue(exception.getMessage().contains("Unknown time unit"));

        // Invalid unit without space
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> DurationUtils.parse("100xs")
        );
        assertTrue(exception.getMessage().contains("Unknown time unit"));
    }

    @Test
    void testParseOrDefault() {
        Duration defaultDuration = Duration.ofSeconds(30);
        
        // Valid input with space
        assertEquals(Duration.ofMillis(1000), DurationUtils.parseOrDefault("1000 ms", defaultDuration));
        
        // Valid input without space
        assertEquals(Duration.ofMillis(1000), DurationUtils.parseOrDefault("1000ms", defaultDuration));
        
        // Invalid input
        assertEquals(defaultDuration, DurationUtils.parseOrDefault("invalid", defaultDuration));
        assertEquals(defaultDuration, DurationUtils.parseOrDefault(null, defaultDuration));
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
