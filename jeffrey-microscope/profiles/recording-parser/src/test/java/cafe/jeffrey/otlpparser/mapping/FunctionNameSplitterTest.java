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
