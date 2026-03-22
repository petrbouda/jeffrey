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
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.profile.common.model.FrameType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameNameBuilderTest {

    private final FrameNameBuilder builder = new FrameNameBuilder();

    @Nested
    class JavaFrames {

        @Test
        void jitCompiledFrameReturnsClassAndMethod() {
            JfrStackFrame frame = createFrame("com.example.MyClass", "doWork", "JIT compiled");

            String result = builder.generateName(frame, createThread("main", 1, 100, false), FrameType.JIT_COMPILED);

            assertEquals("com.example.MyClass#doWork", result);
        }

        @Test
        void c1CompiledFrameReturnsClassAndMethod() {
            JfrStackFrame frame = createFrame("java.util.HashMap", "put", "C1 compiled");

            String result = builder.generateName(frame, createThread("main", 1, 100, false), FrameType.C1_COMPILED);

            assertEquals("java.util.HashMap#put", result);
        }

        @Test
        void interpretedFrameReturnsClassAndMethod() {
            JfrStackFrame frame = createFrame("org.example.Service", "process", "Interpreted");

            String result = builder.generateName(frame, createThread("worker", 2, 101, false), FrameType.INTERPRETED);

            assertEquals("org.example.Service#process", result);
        }

        @Test
        void inlinedFrameReturnsClassAndMethod() {
            JfrStackFrame frame = createFrame("java.lang.String", "length", "Inlined");

            String result = builder.generateName(frame, createThread("main", 1, 100, false), FrameType.INLINED);

            assertEquals("java.lang.String#length", result);
        }

        @Test
        void generateNameWithoutFrameTypeDeterminesTypeFromFrame() {
            JfrStackFrame frame = createFrame("com.example.App", "run", "JIT compiled");

            String result = builder.generateName(frame, createThread("main", 1, 100, false));

            assertEquals("com.example.App#run", result);
        }
    }

    @Nested
    class NativeFrames {

        @Test
        void cppFrameReturnsMethodNameOnly() {
            JfrStackFrame frame = createFrame("", "JVM_Sleep", "C++");

            String result = builder.generateName(frame, createThread("main", 1, 100, false), FrameType.CPP);

            assertEquals("JVM_Sleep", result);
        }

        @Test
        void kernelFrameReturnsMethodNameOnly() {
            JfrStackFrame frame = createFrame("", "syscall_enter", "Kernel");

            String result = builder.generateName(frame, createThread("main", 1, 100, false), FrameType.KERNEL);

            assertEquals("syscall_enter", result);
        }

        @Test
        void nativeFrameReturnsMethodNameOnly() {
            JfrStackFrame frame = createFrame("", "pthread_mutex_lock", "Native");

            String result = builder.generateName(frame, createThread("main", 1, 100, false), FrameType.NATIVE);

            assertEquals("pthread_mutex_lock", result);
        }
    }

    @Nested
    class ThreadNameSynthetic {

        @Test
        void platformThreadWithJavaThreadId() {
            JfrStackFrame frame = createFrame("", "", "Thread Name (Synthetic)");
            JfrThread thread = createThread("main", 1, 100, false);

            String result = builder.generateName(frame, thread, FrameType.THREAD_NAME_SYNTHETIC);

            assertEquals("main (1)", result);
        }

        @Test
        void platformThreadWithoutJavaThreadId() {
            JfrStackFrame frame = createFrame("", "", "Thread Name (Synthetic)");
            JfrThread thread = createThread("GC Thread", 0, 12345, false);

            String result = builder.generateName(frame, thread, FrameType.THREAD_NAME_SYNTHETIC);

            assertEquals("GC Thread (12345)", result);
        }

        @Test
        void virtualThreadWithJavaThreadId() {
            JfrStackFrame frame = createFrame("", "", "Thread Name (Synthetic)");
            JfrThread thread = createThread("VirtualThread-1", 999, 200, true);

            String result = builder.generateName(frame, thread, FrameType.THREAD_NAME_SYNTHETIC);

            assertEquals("VirtualThread-1 (999) (V)", result);
        }

        @Test
        void virtualThreadWithoutJavaThreadId() {
            JfrStackFrame frame = createFrame("", "", "Thread Name (Synthetic)");
            JfrThread thread = createThread("virtual-worker", 0, 54321, true);

            String result = builder.generateName(frame, thread, FrameType.THREAD_NAME_SYNTHETIC);

            assertEquals("virtual-worker (54321) (V)", result);
        }

        @Test
        void negativeJavaThreadIdUsesOsThreadId() {
            JfrStackFrame frame = createFrame("", "", "Thread Name (Synthetic)");
            JfrThread thread = createThread("JIT Compiler", -1, 777, false);

            String result = builder.generateName(frame, thread, FrameType.THREAD_NAME_SYNTHETIC);

            assertEquals("JIT Compiler (777)", result);
        }
    }

    @Nested
    class MethodNameBasedThread {

        @Test
        void platformThreadWithJavaThreadId() {
            JfrThread thread = createThread("http-nio-8080-exec-1", 42, 1001, false);

            String result = FrameNameBuilder.methodNameBasedThread(thread);

            assertEquals("http-nio-8080-exec-1 (42)", result);
        }

        @Test
        void platformThreadWithZeroJavaThreadId() {
            JfrThread thread = createThread("C2 CompilerThread0", 0, 2002, false);

            String result = FrameNameBuilder.methodNameBasedThread(thread);

            assertEquals("C2 CompilerThread0 (2002)", result);
        }

        @Test
        void virtualThreadAppendsVirtualMarker() {
            JfrThread thread = createThread("carrier-thread", 100, 3003, true);

            String result = FrameNameBuilder.methodNameBasedThread(thread);

            assertEquals("carrier-thread (100) (V)", result);
        }

        @Test
        void virtualThreadWithZeroJavaThreadIdUsesOsId() {
            JfrThread thread = createThread("vthread", 0, 4004, true);

            String result = FrameNameBuilder.methodNameBasedThread(thread);

            assertEquals("vthread (4004) (V)", result);
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void unknownFrameTypeThrowsException() {
            JfrStackFrame frame = createFrame("com.example.Test", "test", "Unknown");

            assertThrows(IllegalArgumentException.class, () ->
                    builder.generateName(frame, createThread("main", 1, 100, false), FrameType.UNKNOWN));
        }
    }

    private static JfrStackFrame createFrame(String className, String methodName, String type) {
        return new JfrStackFrame() {
            @Override
            public String type() {
                return type;
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

    private static JfrThread createThread(String name, long javaThreadId, long osThreadId, boolean isVirtual) {
        return new JfrThread() {
            @Override
            public long osThreadId() {
                return osThreadId;
            }

            @Override
            public long javaThreadId() {
                return javaThreadId;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean isVirtual() {
                return isVirtual;
            }
        };
    }
}
