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

package cafe.jeffrey.flamegraph.code;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodRefTest {

    private static boolean matches(String reference, String className, String methodName) {
        return MethodRef.parse(reference).matcher().matches(new MethodKey(className, methodName));
    }

    @Nested
    class Parsing {

        @Test
        void splitsClassAndMethod() {
            MethodRef ref = MethodRef.parse(" com.acme.Checkout#checkout ");
            assertEquals("com.acme.Checkout", ref.className());
            assertEquals("checkout", ref.methodName());
        }

        @Test
        void aBareClassNamesTheWholeClass() {
            MethodRef ref = MethodRef.parse("com.acme.Checkout");
            assertNull(ref.methodName());
            assertTrue(ref.wholeClass());
        }

        @Test
        void refusesABlankReference() {
            assertThrows(IllegalArgumentException.class, () -> MethodRef.parse(" "));
        }
    }

    @Nested
    class RuntimeNames {

        @Test
        void matchesTheMethodItself() {
            assertTrue(matches("com.acme.Checkout#checkout", "com.acme.Checkout", "checkout"));
            assertFalse(matches("com.acme.Checkout#checkout", "com.acme.Checkout", "checkoutAll"));
        }

        @Test
        void matchesJavacLambdaBodies() {
            assertTrue(matches("com.acme.Checkout#checkout", "com.acme.Checkout", "lambda$checkout$1"));
            assertFalse(matches("com.acme.Checkout#checkout", "com.acme.Checkout", "lambda$refund$1"));
        }

        @Test
        void matchesKotlinGeneratedNames() {
            assertTrue(matches("com.acme.Checkout#checkout", "com.acme.Checkout", "checkout$default"));
            assertTrue(matches("com.acme.Checkout#checkout", "com.acme.Checkout", "checkout$lambda$0"));
            assertTrue(matches("com.acme.Checkout#checkout", "com.acme.Checkout$checkout$1", "invokeSuspend"));
            assertTrue(matches("com.acme.Checkout#checkout", "com.acme.CheckoutKt", "checkout"));
        }

        @Test
        void acceptsTheSourceSpellingOfANestedClass() {
            assertTrue(matches("com.acme.Checkout.Pricing#price", "com.acme.Checkout$Pricing", "price"));
        }

        @Test
        void aWholeClassTakesItsNestedClasses() {
            assertTrue(matches("com.acme.Checkout", "com.acme.Checkout$1", "run"));
            assertFalse(matches("com.acme.Checkout", "com.acme.CheckoutService", "run"));
        }

        @Test
        void neverMatchesANativeFrame() {
            assertFalse(matches("com.acme.Checkout", "", "read"));
        }
    }
}
