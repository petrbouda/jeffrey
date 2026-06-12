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

package cafe.jeffrey.shared.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationUtilsTest {

    @Nested
    class Format {

        @Test
        void oneMillisecondHasNoTrailingZeroParts() {
            assertEquals("1ms", DurationUtils.format(Duration.ofMillis(1)));
        }

        @Test
        void fiveMillisecondsHasNoTrailingZeroParts() {
            assertEquals("5ms", DurationUtils.format(Duration.ofMillis(5)));
        }

        @Test
        void millisecondsWithMicroseconds() {
            assertEquals("1ms 5µs", DurationUtils.format(Duration.ofMillis(1).plusNanos(5_000)));
        }

        @Test
        void millisecondsWithNanoseconds() {
            assertEquals("1ms 5ns", DurationUtils.format(Duration.ofMillis(1).plusNanos(5)));
        }

        @Test
        void pureNanoseconds() {
            assertEquals("999ns", DurationUtils.format(Duration.ofNanos(999)));
        }

        @Test
        void microsecondsWithNanoseconds() {
            assertEquals("1µs 5ns", DurationUtils.format(Duration.ofNanos(1_005)));
        }

        @Test
        void secondsWithMillis() {
            assertEquals("2s 5ms", DurationUtils.format(Duration.ofSeconds(2).plusMillis(5)));
        }
    }

    @Nested
    class FormatNanos2Units {

        @Test
        void fiveMillisecondsShowsOnlyMillis() {
            assertEquals("5ms", DurationUtils.formatNanos2Units(Duration.ofMillis(5).toNanos()));
        }

        @Test
        void millisAndMicrosShowsTwoParts() {
            assertEquals("1ms 5µs", DurationUtils.formatNanos2Units(1_005_000L));
        }

        @Test
        void threePartsAreCappedAtTwo() {
            // 1ms 2µs 3ns -> only the two most significant parts
            assertEquals("1ms 2µs", DurationUtils.formatNanos2Units(1_002_003L));
        }
    }

    @Nested
    class Parse {

        @Test
        void parsesMillisWithSpace() {
            assertEquals(Duration.ofMillis(1000), DurationUtils.parse("1000 ms"));
        }

        @Test
        void parsesMillisWithoutSpace() {
            assertEquals(Duration.ofMillis(1000), DurationUtils.parse("1000ms"));
        }
    }
}
