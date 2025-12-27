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

package pbouda.jeffrey.frameir.frame;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaMatchUtilsTest {

    @Nested
    class LambdaClassDetection {

        @Test
        void detectsLambdaClassWithHexSuffix() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("ch.qos.logback.classic.joran.JoranConfigurator$$Lambda.0x00007fc6071135c0", "run")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsSimpleLambdaClass() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.MyClass$$Lambda$123", "apply")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsLambdaClassWithNumericSuffix() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.util.stream.ReferencePipeline$$Lambda$456/0x0000000800c12345", "test")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }
    }

    @Nested
    class LambdaFormDetection {

        @Test
        void detectsLambdaFormMH() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.LambdaForm$MH", "invoke")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsLambdaFormDMH() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.LambdaForm$DMH", "invokeStatic")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsLambdaFormBMH() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.LambdaForm$BMH", "reinvoke")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }
    }

    @Nested
    class DirectMethodHandleDetection {

        @Test
        void detectsDirectMethodHandleHolder() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.DirectMethodHandle$Holder", "invokeStatic")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsDirectMethodHandleHolderSubclass() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.DirectMethodHandle$Holder$Inner", "invoke")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }
    }

    @Nested
    class LambdaMethodDetection {

        @Test
        void detectsLambdaMethodWithNumber() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.MyClass", "lambda$main$0")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsLambdaMethodWithMethodName() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.Service", "lambda$processItems$5")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void detectsNestedLambdaMethod() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.Handler", "lambda$handle$outer$inner$2")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }
    }

    @Nested
    class NonLambdaFrames {

        @Test
        void regularClassNotDetectedAsLambda() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.util.ArrayList", "add")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void classWithLambdaInNameButNotLambdaPattern() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.LambdaHandler", "handle")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void methodWithLambdaInNameButNotPrefix() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.MyClass", "processLambda")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void dollarSignInClassNameNotLambda() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.Outer$Inner", "doWork")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void regularInvokeClass() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.MethodHandle", "invoke")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }

        @Test
        void directMethodHandleButNotHolder() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.DirectMethodHandle", "invoke")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
        }
    }

    @Nested
    class StacktraceIndexHandling {

        @Test
        void detectsLambdaAtMiddleOfStacktrace() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("com.example.Main", "main"),
                    createFrame("com.example.Service$$Lambda$1", "apply"),
                    createFrame("java.util.stream.ReferencePipeline", "map")
            );

            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 1));
            assertFalse(LambdaMatchUtils.matchLambdaFrames(stacktrace, 2));
        }

        @Test
        void detectsMultipleLambdaFramesInStacktrace() {
            List<JfrStackFrame> stacktrace = List.of(
                    createFrame("java.lang.invoke.LambdaForm$MH", "invoke"),
                    createFrame("com.example.MyClass", "lambda$run$0"),
                    createFrame("com.example.Handler$$Lambda$42", "accept")
            );

            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 0));
            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 1));
            assertTrue(LambdaMatchUtils.matchLambdaFrames(stacktrace, 2));
        }
    }

    private static JfrStackFrame createFrame(String className, String methodName) {
        return new JfrStackFrame() {
            @Override
            public String type() {
                return "JIT compiled";
            }

            @Override
            public int lineNumber() {
                return 0;
            }

            @Override
            public int bytecodeIndex() {
                return 0;
            }

            @Override
            public JfrMethod method() {
                return new JfrMethod() {
                    @Override
                    public JfrClass clazz() {
                        return () -> className;
                    }

                    @Override
                    public String methodName() {
                        return methodName;
                    }
                };
            }
        };
    }
}
