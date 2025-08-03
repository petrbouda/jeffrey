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

package pbouda.jeffrey.common.model.repository.matcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsprofCacheFileMatcherTest {

    private AsprofCacheFileMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new AsprofCacheFileMatcher();
    }

    @Nested
    @DisplayName("Valid Cache Files")
    class ValidCacheFiles {

        @Test
        @DisplayName("Should match simple JFR cache file with single digit number")
        void shouldMatchSimpleJfrCacheFileWithSingleDigit() {
            assertTrue(matcher.test("profile.jfr.1~"));
        }

        @Test
        @DisplayName("Should match JFR cache file with multiple digit number")
        void shouldMatchJfrCacheFileWithMultipleDigits() {
            assertTrue(matcher.test("profile.jfr.123~"));
        }

        @Test
        @DisplayName("Should match complex filename with timestamp")
        void shouldMatchComplexFilenameWithTimestamp() {
            assertTrue(matcher.test("profile-20250803-090556.jfr.1~"));
        }

        @Test
        @DisplayName("Should match simple hello JFR cache file")
        void shouldMatchSimpleHelloJfrCacheFile() {
            assertTrue(matcher.test("hello.jfr.2~"));
        }

        @Test
        @DisplayName("Should match complex filename with dashes and dots")
        void shouldMatchComplexFilenameWithDashesAndDots() {
            assertTrue(matcher.test("p-12222-23.2333.jfr.11~"));
        }

        @Test
        @DisplayName("Should match filename with very large number")
        void shouldMatchFilenameWithVeryLargeNumber() {
            assertTrue(matcher.test("recording.jfr.999999~"));
        }

        @Test
        @DisplayName("Should match filename with path prefix")
        void shouldMatchFilenameWithPathPrefix() {
            assertTrue(matcher.test("/path/to/recording.jfr.42~"));
        }

        @Test
        @DisplayName("Should match filename with spaces (encoded or actual)")
        void shouldMatchFilenameWithSpaces() {
            assertTrue(matcher.test("my recording file.jfr.7~"));
        }

        @Test
        @DisplayName("Should match filename with underscores")
        void shouldMatchFilenameWithUnderscores() {
            assertTrue(matcher.test("my_recording_file.jfr.8~"));
        }

        @Test
        @DisplayName("Should match filename with special characters")
        void shouldMatchFilenameWithSpecialCharacters() {
            assertTrue(matcher.test("recording@server#1.jfr.99~"));
        }

        @Test
        @DisplayName("Should match zero-numbered cache file")
        void shouldMatchZeroNumberedCacheFile() {
            assertTrue(matcher.test("recording.jfr.0~"));
        }
    }

    @Nested
    @DisplayName("Invalid Cache Files")
    class InvalidCacheFiles {

        @Test
        @DisplayName("Should not match null filename")
        void shouldNotMatchNullFilename() {
            assertFalse(matcher.test(null));
        }

        @Test
        @DisplayName("Should not match empty filename")
        void shouldNotMatchEmptyFilename() {
            assertFalse(matcher.test(""));
        }

        @Test
        @DisplayName("Should not match regular JFR file without cache suffix")
        void shouldNotMatchRegularJfrFileWithoutCacheSuffix() {
            assertFalse(matcher.test("profile.jfr"));
        }

        @Test
        @DisplayName("Should not match JFR file with cache suffix but no number")
        void shouldNotMatchJfrFileWithCacheSuffixButNoNumber() {
            assertFalse(matcher.test("profile.jfr.~"));
        }

        @Test
        @DisplayName("Should not match JFR file with non-numeric cache suffix")
        void shouldNotMatchJfrFileWithNonNumericCacheSuffix() {
            assertFalse(matcher.test("profile.jfr.abc~"));
        }

        @Test
        @DisplayName("Should not match JFR file without tilde at the end")
        void shouldNotMatchJfrFileWithoutTildeAtEnd() {
            assertFalse(matcher.test("profile.jfr.1"));
        }

        @Test
        @DisplayName("Should not match non-JFR file with cache-like suffix")
        void shouldNotMatchNonJfrFileWithCacheLikeSuffix() {
            assertFalse(matcher.test("profile.txt.1~"));
        }

        @Test
        @DisplayName("Should not match file with JFR extension but wrong cache format")
        void shouldNotMatchFileWithJfrExtensionButWrongCacheFormat() {
            assertFalse(matcher.test("profile.jfr1~"));
        }

        @Test
        @DisplayName("Should not match file with decimal number in cache suffix")
        void shouldNotMatchFileWithDecimalNumberInCacheSuffix() {
            assertFalse(matcher.test("profile.jfr.1.5~"));
        }

        @Test
        @DisplayName("Should not match file with negative number in cache suffix")
        void shouldNotMatchFileWithNegativeNumberInCacheSuffix() {
            assertFalse(matcher.test("profile.jfr.-1~"));
        }

        @Test
        @DisplayName("Should not match file with mixed alphanumeric cache suffix")
        void shouldNotMatchFileWithMixedAlphanumericCacheSuffix() {
            assertFalse(matcher.test("profile.jfr.1a~"));
        }

        @Test
        @DisplayName("Should not match file with extra characters after tilde")
        void shouldNotMatchFileWithExtraCharactersAfterTilde() {
            assertFalse(matcher.test("profile.jfr.1~extra"));
        }

        @Test
        @DisplayName("Should not match file with space in cache number")
        void shouldNotMatchFileWithSpaceInCacheNumber() {
            assertFalse(matcher.test("profile.jfr.1 2~"));
        }

        @Test
        @DisplayName("Should not match file with multiple dots before number")
        void shouldNotMatchFileWithMultipleDotsBeforeNumber() {
            assertFalse(matcher.test("profile.jfr..1~"));
        }

        @Test
        @DisplayName("Should not match file without JFR extension")
        void shouldNotMatchFileWithoutJfrExtension() {
            assertFalse(matcher.test("profile.1~"));
        }

        @Test
        @DisplayName("Should not match file with wrong extension case")
        void shouldNotMatchFileWithWrongExtensionCase() {
            assertFalse(matcher.test("profile.JFR.1~"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should not match whitespace-only filename")
        void shouldNotMatchWhitespaceOnlyFilename() {
            assertFalse(matcher.test("   "));
        }

        @Test
        @DisplayName("Should handle very long filename")
        void shouldHandleVeryLongFilename() {
            String longFilename = "a".repeat(1000) + ".jfr.1~";
            assertTrue(matcher.test(longFilename));
        }

        @Test
        @DisplayName("Should handle very long number in cache suffix")
        void shouldHandleVeryLongNumberInCacheSuffix() {
            String longNumber = "1".repeat(50);
            assertTrue(matcher.test("profile.jfr." + longNumber + "~"));
        }

        @Test
        @DisplayName("Should match filename starting with dot")
        void shouldMatchFilenameStartingWithDot() {
            assertTrue(matcher.test(".hidden-profile.jfr.1~"));
        }

        @Test
        @DisplayName("Should match filename with multiple JFR-like extensions")
        void shouldMatchFilenameWithMultipleJfrLikeExtensions() {
            assertTrue(matcher.test("profile.jfr.backup.jfr.1~"));
        }
    }
}