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

package cafe.jeffrey.microscope.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPatternTest {

    @Nested
    class Literal {

        @Test
        void aPlainSubstring() {
            assertTrue(SearchPattern.of("OrderService").matches("com.example.OrderService#place"));
        }

        @Test
        void anInnerClassMatchesItselfDespiteTheDollar() {
            assertTrue(SearchPattern.of("Outer$Inner#run").matches("com.example.Outer$Inner#run"));
        }

        @Test
        void anArrayTypeMatchesItselfAlthoughItDoesNotCompile() {
            SearchPattern pattern = SearchPattern.of("byte[]");

            assertTrue(pattern.matches("byte[]"));
            assertNull(pattern.regexOrNull());
        }

        @Test
        void anUnbalancedParenthesisIsSearchedLiterally() {
            SearchPattern pattern = SearchPattern.of("parse(");

            assertTrue(pattern.matches("Parser#parse(String)"));
            assertFalse(pattern.matches("Parser#parse"));
        }
    }

    @Nested
    class Regex {

        @Test
        void anAlternationIsFoundAnywhereInTheName() {
            SearchPattern pattern = SearchPattern.of("Order|Payment");

            assertTrue(pattern.matches("com.example.PaymentGateway#charge"));
            assertFalse(pattern.matches("com.example.Shipping#ship"));
        }

        @Test
        void aDotStillSpansTheClassMethodBoundary() {
            assertTrue(SearchPattern.of("JsonReader.<init>").matches("JsonReader#<init>"));
        }

        @Test
        void aCompilingPatternIsHandedOnForTheRegexReading() {
            assertEquals("Order|Payment", SearchPattern.of("Order|Payment").regexOrNull());
        }
    }
}
