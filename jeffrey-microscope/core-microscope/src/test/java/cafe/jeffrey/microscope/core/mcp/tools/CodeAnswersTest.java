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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.code.MethodKey;
import cafe.jeffrey.flamegraph.code.MethodRef;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAnswersTest {

    @Nested
    class NearbyMethods {

        @Test
        void classContextRanksAheadOfAHotUnrelatedMethodWithTheSameName() {
            MethodValue unrelated = new MethodValue(new MethodKey("com.other.Worker", "save"), 10000);
            MethodValue related = new MethodValue(new MethodKey("com.acme.Orders$Repository", "save"), 10);

            List<MethodValue> nearest = CodeAnswers.nearest(MethodRef.parse("com.acme.Orders#save"),
                    List.of(unrelated, related));

            assertEquals(List.of(related, unrelated), nearest);
        }

        @Test
        void aClassmateRanksAheadOfAnUnrelatedNamesake() {
            MethodValue unrelated = new MethodValue(new MethodKey("com.other.Worker", "save"), 10000);
            MethodValue related = new MethodValue(new MethodKey("com.acme.Orders", "load"), 10);

            assertEquals(List.of(related, unrelated), CodeAnswers.nearest(MethodRef.parse("com.acme.Orders#save"),
                    List.of(unrelated, related)));
        }
    }

    private static boolean finds(String search, String frame) {
        return Pattern.compile(search).matcher(frame).find();
    }

    @Nested
    class SearchOfAReference {

        @Test
        void sourceSpellingFindsTheNestedClassesRuntimeFrames() {
            String search = CodeAnswers.search(MethodRef.parse("com.acme.Checkout.PriceCalculator#formatLine"));
            assertTrue(finds(search, "com.acme.Checkout$PriceCalculator#formatLine"));
            assertTrue(finds(search, "com.acme.Checkout.PriceCalculator#formatLine"));
            assertFalse(finds(search, "com.acme.Checkout$PriceCalculator#reprice"));
        }

        @Test
        void binarySpellingIsFoundAsWritten() {
            String search = CodeAnswers.search(MethodRef.parse("com.acme.Checkout$PriceCalculator#formatLine"));
            assertTrue(finds(search, "com.acme.Checkout$PriceCalculator#formatLine"));
        }

        @Test
        void wholeClassFindsEveryMethodOfIt() {
            String search = CodeAnswers.search(MethodRef.parse("com.acme.Checkout.SessionCache"));
            assertEquals("com[.$]acme[.$]Checkout[.$]SessionCache", search);
            assertTrue(finds(search, "com.acme.Checkout$SessionCache#touch"));
        }

        @Test
        void aDotIsNotAWildcard() {
            String search = CodeAnswers.search(MethodRef.parse("com.acme.Checkout#run"));
            assertFalse(finds(search, "comXacme.Checkout#run"));
        }
    }

    @Nested
    class SearchOfAFrame {

        @Test
        void runtimeFrameIsEscapedAsWritten() {
            String search = CodeAnswers.search(new MethodKey("com.acme.Checkout$PriceCalculator", "lambda$reprice$0"));
            assertEquals("com\\.acme\\.Checkout\\$PriceCalculator#lambda\\$reprice\\$0", search);
            assertTrue(finds(search, "com.acme.Checkout$PriceCalculator#lambda$reprice$0"));
        }
    }
}
