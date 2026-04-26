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

package cafe.jeffrey.profile.parser.stacktrace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.StacktraceType;
import cafe.jeffrey.provider.profile.model.EventFrame;
import cafe.jeffrey.provider.profile.model.EventThread;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("OverallStacktraceTypeResolver")
class OverallStacktraceTypeResolverTest {

    private OverallStacktraceTypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OverallStacktraceTypeResolver();
    }

    @Nested
    @DisplayName("Thread-based resolution")
    class ThreadBasedResolution {

        @Test
        @DisplayName("C1 compiler thread resolves to JVM_JIT")
        void c1CompilerThread() {
            resolver.applyThread(new EventThread("C1 CompilerThread0", 100L, null, false));
            assertEquals(StacktraceType.JVM_JIT, resolver.resolve());
        }

        @Test
        @DisplayName("C2 compiler thread resolves to JVM_JIT")
        void c2CompilerThread() {
            resolver.applyThread(new EventThread("C2 CompilerThread1", 101L, null, false));
            assertEquals(StacktraceType.JVM_JIT, resolver.resolve());
        }

        @Test
        @DisplayName("GC Thread resolves to JVM_GC")
        void gcThread() {
            resolver.applyThread(new EventThread("GC Thread#0", 200L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("G1 thread resolves to JVM_GC")
        void g1Thread() {
            resolver.applyThread(new EventThread("G1 Young RemSet Sampling", 201L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("Shenandoah thread resolves to JVM_GC")
        void shenandoahThread() {
            resolver.applyThread(new EventThread("Shenandoah GC Thread", 202L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("ZGC driver thread resolves to JVM_GC")
        void zDriverThread() {
            resolver.applyThread(new EventThread("ZDriver", 203L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("ZGC worker thread resolves to JVM_GC")
        void zWorkerThread() {
            resolver.applyThread(new EventThread("ZWorker#0", 204L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("Non-generational XDriver thread resolves to JVM_GC")
        void xDriverThread() {
            resolver.applyThread(new EventThread("XDriver", 205L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("JFR Periodic Tasks thread resolves to JVM_JFR")
        void jfrPeriodicTasksThread() {
            resolver.applyThread(new EventThread("JFR Periodic Tasks", 300L, null, false));
            assertEquals(StacktraceType.JVM_JFR, resolver.resolve());
        }

        @Test
        @DisplayName("JFR Recorder Thread resolves to JVM_JFR")
        void jfrRecorderThread() {
            resolver.applyThread(new EventThread("JFR Recorder Thread", 301L, null, false));
            assertEquals(StacktraceType.JVM_JFR, resolver.resolve());
        }

        @Test
        @DisplayName("JFR Shutdown Hook resolves to JVM_JFR")
        void jfrShutdownHookThread() {
            resolver.applyThread(new EventThread("JFR Shutdown Hook", 302L, null, false));
            assertEquals(StacktraceType.JVM_JFR, resolver.resolve());
        }

        @Test
        @DisplayName("VM Thread resolves to JVM")
        void vmThread() {
            resolver.applyThread(new EventThread("VM Thread", 400L, null, false));
            assertEquals(StacktraceType.JVM, resolver.resolve());
        }

        @Test
        @DisplayName("GC thread with javaId set is not recognized as GC (requires javaId == null)")
        void gcThreadWithJavaIdIsNotGC() {
            resolver.applyThread(new EventThread("GC Thread#0", 200L, 1L, false));
            // Should fall through to NATIVE since no frames applied
            assertEquals(StacktraceType.NATIVE, resolver.resolve());
        }

        @Test
        @DisplayName("VM Thread with javaId set is not recognized as VM thread")
        void vmThreadWithJavaIdIsNotVM() {
            resolver.applyThread(new EventThread("VM Thread", 400L, 1L, false));
            assertEquals(StacktraceType.NATIVE, resolver.resolve());
        }
    }

    @Nested
    @DisplayName("Frame-based resolution")
    class FrameBasedResolution {

        @Test
        @DisplayName("Java JIT compiled frame resolves to APPLICATION")
        void javaJitFrame() {
            resolver.applyThread(new EventThread("main", 1L, 1L, false));
            resolver.applyFrame(new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 0, 42));
            assertEquals(StacktraceType.APPLICATION, resolver.resolve());
        }

        @Test
        @DisplayName("Java Interpreted frame resolves to APPLICATION")
        void javaInterpretedFrame() {
            resolver.applyThread(new EventThread("worker-1", 2L, 2L, false));
            resolver.applyFrame(new EventFrame("com.example.MyClass", "compute", "Interpreted", 0, 10));
            assertEquals(StacktraceType.APPLICATION, resolver.resolve());
        }

        @Test
        @DisplayName("Java C1 compiled frame resolves to APPLICATION")
        void javaC1CompiledFrame() {
            resolver.applyThread(new EventThread("pool-1", 3L, 3L, false));
            resolver.applyFrame(new EventFrame("com.example.Service", "handle", "C1 compiled", 0, 5));
            assertEquals(StacktraceType.APPLICATION, resolver.resolve());
        }

        @Test
        @DisplayName("Java Inlined frame resolves to APPLICATION")
        void javaInlinedFrame() {
            resolver.applyThread(new EventThread("pool-2", 4L, 4L, false));
            resolver.applyFrame(new EventFrame("com.example.Util", "add", "Inlined", 0, 1));
            assertEquals(StacktraceType.APPLICATION, resolver.resolve());
        }

        @Test
        @DisplayName("CPP thread_native_entry frame resolves to JVM")
        void cppThreadNativeEntryFrame() {
            resolver.applyThread(new EventThread("worker", 5L, 5L, false));
            resolver.applyFrame(new EventFrame("", "thread_native_entry", "C++", 0, 0));
            assertEquals(StacktraceType.JVM, resolver.resolve());
        }

        @Test
        @DisplayName("unknown_Java method resolves to UNKNOWN")
        void unknownJavaFrame() {
            resolver.applyThread(new EventThread("main", 1L, 1L, false));
            resolver.applyFrame(new EventFrame("", "unknown_Java_frame", "C++", 0, 0));
            assertEquals(StacktraceType.UNKNOWN, resolver.resolve());
        }

        @Test
        @DisplayName("no_Java_frame method resolves to UNKNOWN")
        void noJavaFrame() {
            resolver.applyThread(new EventThread("main", 1L, 1L, false));
            resolver.applyFrame(new EventFrame("", "no_Java_frame", "C++", 0, 0));
            assertEquals(StacktraceType.UNKNOWN, resolver.resolve());
        }

        @Test
        @DisplayName("not_walkable_Java method resolves to UNKNOWN")
        void notWalkableJavaFrame() {
            resolver.applyThread(new EventThread("main", 1L, 1L, false));
            resolver.applyFrame(new EventFrame("", "not_walkable_Java_frame", "C++", 0, 0));
            assertEquals(StacktraceType.UNKNOWN, resolver.resolve());
        }

        @Test
        @DisplayName("No frames and no special thread resolves to NATIVE")
        void noFramesResolvesToNative() {
            resolver.applyThread(new EventThread("main", 1L, 1L, false));
            assertEquals(StacktraceType.NATIVE, resolver.resolve());
        }

        @Test
        @DisplayName("Only CPP/Native frames without Java frames resolve to NATIVE")
        void onlyCppFrames() {
            resolver.applyThread(new EventThread("main", 1L, 1L, false));
            resolver.applyFrame(new EventFrame("", "some_cpp_func", "C++", 0, 0));
            resolver.applyFrame(new EventFrame("", "another_native", "Native", 0, 0));
            assertEquals(StacktraceType.NATIVE, resolver.resolve());
        }
    }

    @Nested
    @DisplayName("VM Thread with GC frame escalation")
    class VMThreadGCFrameEscalation {

        @Test
        @DisplayName("VM Thread with VM_GenCollectForAllocation frame escalates to JVM_GC")
        void vmThreadWithGCFrame() {
            resolver.applyThread(new EventThread("VM Thread", 400L, null, false));
            assertEquals(StacktraceType.JVM, resolver.resolve());

            resolver.applyFrame(new EventFrame("", "VM_GenCollectForAllocation::doit", "C++", 0, 0));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("VM Thread without GC frame stays as JVM")
        void vmThreadWithoutGCFrame() {
            resolver.applyThread(new EventThread("VM Thread", 400L, null, false));
            resolver.applyFrame(new EventFrame("", "some_other_method", "C++", 0, 0));
            assertEquals(StacktraceType.JVM, resolver.resolve());
        }
    }

    @Nested
    @DisplayName("Thread type takes priority over frame type")
    class ThreadPriority {

        @Test
        @DisplayName("Compiler thread type is not overridden by Java frames")
        void compilerThreadNotOverriddenByJavaFrame() {
            resolver.applyThread(new EventThread("C2 CompilerThread0", 100L, null, false));
            resolver.applyFrame(new EventFrame("com.example.App", "main", "JIT compiled", 0, 1));
            assertEquals(StacktraceType.JVM_JIT, resolver.resolve());
        }

        @Test
        @DisplayName("GC thread type is not overridden by Java frames")
        void gcThreadNotOverriddenByJavaFrame() {
            resolver.applyThread(new EventThread("GC Thread#0", 200L, null, false));
            resolver.applyFrame(new EventFrame("com.example.App", "main", "JIT compiled", 0, 1));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }
    }

    @Nested
    @DisplayName("Null thread name handling")
    class NullThreadName {

        @Test
        @DisplayName("Thread with null name is not classified by thread rules")
        void nullThreadName() {
            resolver.applyThread(new EventThread(null, 1L, null, false));
            assertEquals(StacktraceType.NATIVE, resolver.resolve());
        }
    }

    @Nested
    @DisplayName("ZGC variants")
    class ZGCVariants {

        @Test
        @DisplayName("ZDirector resolves to JVM_GC")
        void zDirector() {
            resolver.applyThread(new EventThread("ZDirector", 1L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("ZStat resolves to JVM_GC")
        void zStat() {
            resolver.applyThread(new EventThread("ZStat", 2L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("ZUncommitter resolves to JVM_GC")
        void zUncommitter() {
            resolver.applyThread(new EventThread("ZUncommitter", 3L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("XWorker resolves to JVM_GC")
        void xWorker() {
            resolver.applyThread(new EventThread("XWorker#0", 4L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("XStat resolves to JVM_GC")
        void xStat() {
            resolver.applyThread(new EventThread("XStat", 5L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }

        @Test
        @DisplayName("XUncommitter resolves to JVM_GC")
        void xUncommitter() {
            resolver.applyThread(new EventThread("XUncommitter", 6L, null, false));
            assertEquals(StacktraceType.JVM_GC, resolver.resolve());
        }
    }
}
