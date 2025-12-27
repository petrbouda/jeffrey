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
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BytesUtilsTest {

    @Nested
    class BytesRange {

        @Test
        void zeroBytes() {
            assertEquals("0 B", BytesUtils.format(0));
        }

        @Test
        void oneByteReturnsBytes() {
            assertEquals("1 B", BytesUtils.format(1));
        }

        @Test
        void maxBytesBeforeKiB() {
            assertEquals("1023 B", BytesUtils.format(1023));
        }
    }

    @Nested
    class KibRange {

        @Test
        void exactlyOneKiB() {
            assertEquals("1.0 KiB", BytesUtils.format(1024));
        }

        @Test
        void oneAndHalfKiB() {
            assertEquals("1.5 KiB", BytesUtils.format(1536));
        }

        @Test
        void largeKiBValue() {
            assertEquals("512.0 KiB", BytesUtils.format(512 * 1024));
        }
    }

    @Nested
    class MibRange {

        @Test
        void exactlyOneMiB() {
            assertEquals("1.0 MiB", BytesUtils.format(1024 * 1024));
        }

        @Test
        void tenMiB() {
            assertEquals("10.0 MiB", BytesUtils.format(10 * 1024 * 1024));
        }

        @Test
        void largeMiBValue() {
            assertEquals("500.0 MiB", BytesUtils.format(500L * 1024 * 1024));
        }
    }

    @Nested
    class GibRange {

        @Test
        void exactlyOneGiB() {
            assertEquals("1.0 GiB", BytesUtils.format(1024L * 1024 * 1024));
        }

        @Test
        void twoAndHalfGiB() {
            assertEquals("2.5 GiB", BytesUtils.format((long) (2.5 * 1024 * 1024 * 1024)));
        }
    }

    @Nested
    class TibRange {

        @Test
        void exactlyOneTiB() {
            assertEquals("1.0 TiB", BytesUtils.format(1024L * 1024 * 1024 * 1024));
        }

        @Test
        void fiveTiB() {
            assertEquals("5.0 TiB", BytesUtils.format(5L * 1024 * 1024 * 1024 * 1024));
        }
    }

    @Nested
    class PibRange {

        @Test
        void exactlyOnePiB() {
            assertEquals("1.0 PiB", BytesUtils.format(1024L * 1024 * 1024 * 1024 * 1024));
        }
    }

    @Nested
    class EibRange {

        @Test
        void exactlyOneEiB() {
            assertEquals("1.0 EiB", BytesUtils.format(1024L * 1024 * 1024 * 1024 * 1024 * 1024));
        }

        @Test
        void longMaxValue() {
            // Long.MAX_VALUE is approximately 8 EiB
            String result = BytesUtils.format(Long.MAX_VALUE);
            assertEquals("8.0 EiB", result);
        }
    }

    @Nested
    class NegativeValues {

        @Test
        void negativeOneKiB() {
            assertEquals("-1.0 KiB", BytesUtils.format(-1024));
        }

        @Test
        void negativeOneMiB() {
            assertEquals("-1.0 MiB", BytesUtils.format(-1024 * 1024));
        }

        @Test
        void negativeTenBytes() {
            assertEquals("-10 B", BytesUtils.format(-10));
        }

        @Test
        void longMinValue() {
            // Long.MIN_VALUE edge case - handled specially in the code
            String result = BytesUtils.format(Long.MIN_VALUE);
            assertEquals("-8.0 EiB", result);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0 B",
            "512, 512 B",
            "1024, 1.0 KiB",
            "1536, 1.5 KiB",
            "1048576, 1.0 MiB",
            "1073741824, 1.0 GiB",
            "1099511627776, 1.0 TiB"
    })
    void commonSizesFormattedCorrectly(long bytes, String expected) {
        assertEquals(expected, BytesUtils.format(bytes));
    }
}
