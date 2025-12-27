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

package pbouda.jeffrey.provider.writer.sql;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.api.model.EventFrame;

import static org.junit.jupiter.api.Assertions.*;

class StacktraceEncoderTest {

    @Nested
    class SingleFrame {

        @Test
        void encodeSingleFrame() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);

            String result = encoder.addFrame(frame).build();

            assertEquals("com.example.MyClass;doWork;JIT compiled;10;42\n", result);
        }

        @Test
        void frameWithZeroLineAndBci() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("java.lang.Thread", "run", "Interpreted", 0, 0);

            String result = encoder.addFrame(frame).build();

            assertEquals("java.lang.Thread;run;Interpreted;0;0\n", result);
        }

        @Test
        void frameWithLargeLineNumber() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("com.example.LargeClass", "method", "JIT compiled", 999, 10000);

            String result = encoder.addFrame(frame).build();

            assertEquals("com.example.LargeClass;method;JIT compiled;999;10000\n", result);
        }
    }

    @Nested
    class MultipleFrames {

        @Test
        void encodeMultipleFrames() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame1 = new EventFrame("com.example.A", "method1", "JIT compiled", 1, 10);
            EventFrame frame2 = new EventFrame("com.example.B", "method2", "Interpreted", 2, 20);
            EventFrame frame3 = new EventFrame("com.example.C", "method3", "Inlined", 3, 30);

            String result = encoder
                    .addFrame(frame1)
                    .addFrame(frame2)
                    .addFrame(frame3)
                    .build();

            String expected = """
                    com.example.A;method1;JIT compiled;1;10
                    com.example.B;method2;Interpreted;2;20
                    com.example.C;method3;Inlined;3;30
                    """;
            assertEquals(expected, result);
        }

        @Test
        void framesAreSeparatedByNewlines() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame1 = new EventFrame("ClassA", "methodA", "type", 0, 0);
            EventFrame frame2 = new EventFrame("ClassB", "methodB", "type", 0, 0);

            String result = encoder.addFrame(frame1).addFrame(frame2).build();

            String[] lines = result.split("\n");
            assertEquals(2, lines.length);
        }
    }

    @Nested
    class EmptyStacktrace {

        @Test
        void emptyEncoderProducesEmptyString() {
            StacktraceEncoder encoder = new StacktraceEncoder();

            String result = encoder.build();

            assertEquals("", result);
        }
    }

    @Nested
    class SpecialCharacters {

        @Test
        void frameWithNullValues() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame(null, null, null, 0, 0);

            String result = encoder.addFrame(frame).build();

            assertEquals("null;null;null;0;0\n", result);
        }

        @Test
        void frameWithEmptyStrings() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("", "", "", 0, 0);

            String result = encoder.addFrame(frame).build();

            assertEquals(";;;0;0\n", result);
        }

        @Test
        void frameWithSpecialCharactersInClassName() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("com.example.Outer$Inner$$Lambda$123", "apply", "JIT compiled", 5, 15);

            String result = encoder.addFrame(frame).build();

            assertTrue(result.startsWith("com.example.Outer$Inner$$Lambda$123;"));
        }

        @Test
        void frameWithUnicodeCharacters() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("com.example.Tëst", "méthod", "JIT compiled", 1, 1);

            String result = encoder.addFrame(frame).build();

            assertEquals("com.example.Tëst;méthod;JIT compiled;1;1\n", result);
        }
    }

    @Nested
    class DelimiterConstant {

        @Test
        void delimiterIsSemicolon() {
            assertEquals(";", StacktraceEncoder.DELIMITER);
        }
    }

    @Nested
    class FluentInterface {

        @Test
        void addFrameReturnsEncoderInstance() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("A", "b", "c", 0, 0);

            StacktraceEncoder returned = encoder.addFrame(frame);

            assertSame(encoder, returned);
        }

        @Test
        void chainingMultipleAddFrameCalls() {
            StacktraceEncoder encoder = new StacktraceEncoder();

            String result = encoder
                    .addFrame(new EventFrame("A", "a", "t", 0, 0))
                    .addFrame(new EventFrame("B", "b", "t", 0, 0))
                    .addFrame(new EventFrame("C", "c", "t", 0, 0))
                    .addFrame(new EventFrame("D", "d", "t", 0, 0))
                    .addFrame(new EventFrame("E", "e", "t", 0, 0))
                    .build();

            assertEquals(5, result.split("\n").length);
        }
    }

    @Nested
    class NegativeValues {

        @Test
        void frameWithNegativeBci() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("Class", "method", "type", -1, 10);

            String result = encoder.addFrame(frame).build();

            assertEquals("Class;method;type;-1;10\n", result);
        }

        @Test
        void frameWithNegativeLineNumber() {
            StacktraceEncoder encoder = new StacktraceEncoder();
            EventFrame frame = new EventFrame("Class", "method", "type", 5, -1);

            String result = encoder.addFrame(frame).build();

            assertEquals("Class;method;type;5;-1\n", result);
        }
    }
}
