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
