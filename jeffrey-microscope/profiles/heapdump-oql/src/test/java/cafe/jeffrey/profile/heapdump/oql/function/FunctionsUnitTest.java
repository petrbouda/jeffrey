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
package cafe.jeffrey.profile.heapdump.oql.function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for the {@code function/} helpers — no DB, no HeapView.
 */
class FunctionsUnitTest {

    @Nested
    class StringPredicatesTests {

        @Test
        void startsWith() {
            assertEquals(Boolean.TRUE, StringPredicates.startsWith("Hello world", "Hello"));
            assertEquals(Boolean.FALSE, StringPredicates.startsWith("Hello world", "world"));
        }

        @Test
        void endsWith() {
            assertEquals(Boolean.TRUE, StringPredicates.endsWith("Hello.class", ".class"));
            assertEquals(Boolean.FALSE, StringPredicates.endsWith("Hello.class", ".java"));
        }

        @Test
        void contains() {
            assertEquals(Boolean.TRUE, StringPredicates.contains("foo bar baz", "bar"));
            assertEquals(Boolean.FALSE, StringPredicates.contains("foo bar baz", "qux"));
        }

        @Test
        void matchesRegex() {
            assertEquals(Boolean.TRUE, StringPredicates.matchesRegex("foo.class", "^foo\\..*"));
            assertEquals(Boolean.FALSE, StringPredicates.matchesRegex("notfoo", "^foo$"));
        }

        @Test
        void equalsIgnoreCase() {
            assertEquals(Boolean.TRUE, StringPredicates.equalsIgnoreCase("OK", "ok"));
            assertEquals(Boolean.FALSE, StringPredicates.equalsIgnoreCase("ok", "okay"));
        }

        @Test
        void isEmptyString() {
            assertEquals(Boolean.TRUE, StringPredicates.isEmptyString(""));
            assertEquals(Boolean.FALSE, StringPredicates.isEmptyString("x"));
        }

        @Test
        void nullPropagation() {
            assertNull(StringPredicates.startsWith(null, "x"));
            assertNull(StringPredicates.endsWith("x", null));
            assertNull(StringPredicates.contains(null, "x"));
        }
    }

    @Nested
    class StringAccessorsTests {

        @Test
        void stringLength() {
            assertEquals(Integer.valueOf(5), StringAccessors.stringLength("hello"));
        }

        @Test
        void substringClamps() {
            assertEquals("ello", StringAccessors.substring("hello", 1, 5));
            assertEquals("hello", StringAccessors.substring("hello", -1, 99));
        }

        @Test
        void substringWithoutEnd() {
            assertEquals("ello", StringAccessors.substring("hello", 1, null));
        }

        @Test
        void lowerUpperTrim() {
            assertEquals("ab", StringAccessors.lower("AB"));
            assertEquals("AB", StringAccessors.upper("ab"));
            assertEquals("hi", StringAccessors.trim("  hi  "));
        }

        @Test
        void indexOfAndLastIndexOf() {
            assertEquals(Integer.valueOf(0), StringAccessors.indexOf("ababa", "a"));
            assertEquals(Integer.valueOf(4), StringAccessors.lastIndexOf("ababa", "a"));
            assertEquals(Integer.valueOf(-1), StringAccessors.indexOf("abc", "z"));
        }

        @Test
        void charAtClamps() {
            assertEquals("b", StringAccessors.charAt("abc", 1));
            assertNull(StringAccessors.charAt("abc", 99));
            assertNull(StringAccessors.charAt("abc", -1));
        }
    }

    @Nested
    class FuzzyTextTests {

        @Test
        void levenshteinIdentical() {
            assertEquals(Integer.valueOf(0), FuzzyTextFunctions.levenshtein("hello", "hello"));
        }

        @Test
        void levenshteinKnownDistance() {
            assertEquals(Integer.valueOf(3), FuzzyTextFunctions.levenshtein("kitten", "sitting"));
        }

        @Test
        void levenshteinEmpty() {
            assertEquals(Integer.valueOf(5), FuzzyTextFunctions.levenshtein("", "hello"));
            assertEquals(Integer.valueOf(5), FuzzyTextFunctions.levenshtein("hello", ""));
        }

        @Test
        void jaroWinklerSelfIsOne() {
            assertEquals(1.0, FuzzyTextFunctions.jaroWinklerSimilarity("hello", "hello"), 1e-9);
        }

        @Test
        void jaroWinklerKnownPair() {
            Double sim = FuzzyTextFunctions.jaroWinklerSimilarity("MARTHA", "MARHTA");
            assertNotNull(sim);
            // Classic Jaro–Winkler example: ~0.961
            assertTrue(sim > 0.95 && sim < 0.97,
                    () -> "expected ~0.961, got " + sim);
        }

        @Test
        void jaroWinklerNoOverlap() {
            assertEquals(0.0, FuzzyTextFunctions.jaroWinklerSimilarity("abc", "xyz"), 1e-9);
        }
    }

    @Nested
    class StringFunctionsTests {

        @Test
        void toHexFormatsAsZeroXLowerHex() {
            assertEquals("0xcafebabe", StringFunctions.toHex(0xCAFEBABEL));
        }
    }
}
