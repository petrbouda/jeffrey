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

package cafe.jeffrey.profile.heapdump.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClassNameFormatterTest {

    @Nested
    class ScalarClasses {

        @Test
        void simplePackage() {
            assertEquals("java.util.HashMap",
                    ClassNameFormatter.userFacing("java/util/HashMap"));
        }

        @Test
        void nestedClassWithDollar() {
            assertEquals("java.util.Map$Entry",
                    ClassNameFormatter.userFacing("java/util/Map$Entry"));
        }

        @Test
        void rootClassWithNoPackage() {
            assertEquals("MyClass", ClassNameFormatter.userFacing("MyClass"));
        }

        @Test
        void deeplyNestedPackage() {
            assertEquals("secondfoundation.common.marketdata.OrderBookEntry",
                    ClassNameFormatter.userFacing(
                            "secondfoundation/common/marketdata/OrderBookEntry"));
        }
    }

    @Nested
    class ObjectArrays {

        @Test
        void singleDimensionObjectArray() {
            assertEquals("java.lang.Object[]",
                    ClassNameFormatter.userFacing("[Ljava/lang/Object;"));
        }

        @Test
        void multiDimensionObjectArray() {
            assertEquals("java.lang.String[][]",
                    ClassNameFormatter.userFacing("[[Ljava/lang/String;"));
        }

        @Test
        void nestedTypeArray() {
            assertEquals("java.util.HashMap$Node[]",
                    ClassNameFormatter.userFacing("[Ljava/util/HashMap$Node;"));
        }
    }

    @Nested
    class PrimitiveArrays {

        @Test
        void intArray() {
            assertEquals("int[]", ClassNameFormatter.userFacing("[I"));
        }

        @Test
        void byteArray() {
            assertEquals("byte[]", ClassNameFormatter.userFacing("[B"));
        }

        @Test
        void multiDimensionPrimitiveArray() {
            assertEquals("byte[][]", ClassNameFormatter.userFacing("[[B"));
        }

        @Test
        void allEightPrimitiveTypes() {
            assertEquals("boolean[]", ClassNameFormatter.userFacing("[Z"));
            assertEquals("byte[]", ClassNameFormatter.userFacing("[B"));
            assertEquals("char[]", ClassNameFormatter.userFacing("[C"));
            assertEquals("short[]", ClassNameFormatter.userFacing("[S"));
            assertEquals("int[]", ClassNameFormatter.userFacing("[I"));
            assertEquals("long[]", ClassNameFormatter.userFacing("[J"));
            assertEquals("float[]", ClassNameFormatter.userFacing("[F"));
            assertEquals("double[]", ClassNameFormatter.userFacing("[D"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void nullPassesThrough() {
            assertNull(ClassNameFormatter.userFacing(null));
        }

        @Test
        void emptyPassesThrough() {
            assertEquals("", ClassNameFormatter.userFacing(""));
        }

        @Test
        void unrecognisedArrayTagFallsBackToInput() {
            // 'X' isn't a valid HPROF primitive tag — return input rather than mangle.
            assertEquals("[X", ClassNameFormatter.userFacing("[X"));
        }

        @Test
        void allBracketsFallsBackToInput() {
            assertEquals("[[", ClassNameFormatter.userFacing("[["));
        }
    }
}
