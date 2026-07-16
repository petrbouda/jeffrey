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

package cafe.jeffrey.otlpparser.mapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.otlpparser.mapping.FunctionNameSplitter.SplitName;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionNameSplitterTest {

    @Nested
    class JvmNames {

        @Test
        void splitsClassAndMethod() {
            SplitName split = FunctionNameSplitter.split("com.example.Foo.doWork");
            assertEquals("com.example.Foo", split.clazz());
            assertEquals("doWork", split.method());
        }

        @Test
        void stripsMethodSignature() {
            SplitName split = FunctionNameSplitter.split("com.example.Foo.doWork(Ljava/lang/String;)V");
            assertEquals("com.example.Foo", split.clazz());
            assertEquals("doWork", split.method());
        }

        @Test
        void keepsInnerClassInClassPart() {
            SplitName split = FunctionNameSplitter.split("com.example.Foo$Bar.call");
            assertEquals("com.example.Foo$Bar", split.clazz());
            assertEquals("call", split.method());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void nameWithoutDotBecomesMethodOnly() {
            SplitName split = FunctionNameSplitter.split("main");
            assertEquals("", split.clazz());
            assertEquals("main", split.method());
        }

        @Test
        void nullAndBlankProduceEmptyParts() {
            assertEquals(new SplitName("", ""), FunctionNameSplitter.split(null));
            assertEquals(new SplitName("", ""), FunctionNameSplitter.split("  "));
        }
    }
}
