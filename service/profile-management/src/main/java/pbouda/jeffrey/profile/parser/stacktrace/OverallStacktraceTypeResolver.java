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

package pbouda.jeffrey.profile.parser.stacktrace;

import pbouda.jeffrey.common.model.FrameType;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.model.EventFrame;
import pbouda.jeffrey.provider.api.model.EventThread;

public class OverallStacktraceTypeResolver implements StacktraceTypeResolver {

    private StacktraceType resolvedType;

    private Boolean isUnknownJava = null;

    /**
     * Conditional is ACTIVE, it means that the type was identified by the thread,
     * but it needs to be confirmed by the certain frame, otherwise it's very likely
     * an Application stacktrace.
     */
    // private Function<EventFrame, StacktraceType> conditionalType;
    @Override
    public void start(Type type) {
    }

    @Override
    public void applyThread(EventThread thread) {
        if (isCompilerThread(thread)) {
            resolvedType = StacktraceType.JVM_JIT;
        } else if (isGarbageCollectionThread(thread)) {
            resolvedType = StacktraceType.JVM_GC;
        } else if (isJFRThread(thread)) {
            resolvedType = StacktraceType.JVM_JFR;
        } else if (isVmThread(thread)) {
            resolvedType = StacktraceType.JVM;
        }
    }

    private static boolean isJFRThread(EventThread thread) {
        String osThreadName = thread.name();
        return osThreadName != null && (
                osThreadName.startsWith("JFR Periodic Tasks")
                        || osThreadName.startsWith("JFR Recorder Thread")
                        || osThreadName.startsWith("JFR Shutdown Hook"));
    }

    private static boolean isCompilerThread(EventThread thread) {
        String osThreadName = thread.name();
        return osThreadName != null &&
                (osThreadName.startsWith("C1 CompilerT") || osThreadName.startsWith("C2 CompilerT"));
    }

    private static boolean isGarbageCollectionThread(EventThread thread) {
        String osThreadName = thread.name();
        return osThreadName != null && thread.javaId() == null && (
                isGCStopTheWorld(osThreadName)
                        || isG1(osThreadName)
                        || isShenandoah(osThreadName)
                        || isGenerationalZ(osThreadName)
                        || isNonGenerationalZ(osThreadName)
        );
    }

    /**
     * Stop-the-world garbage collection threads for parallel processing.
     * - G1 Young Generation
     * - Parallel GC
     * - ...
     */
    private static boolean isGCStopTheWorld(String threadName) {
        return threadName.startsWith("GC Thread");
    }

    private static boolean isG1(String threadName) {
        return threadName.startsWith("G1 ");
    }

    private static boolean isShenandoah(String threadName) {
        return threadName.startsWith("Shenandoah ");
    }

    private static boolean isNonGenerationalZ(String threadName) {
        return threadName.startsWith("XDriver")
                || threadName.startsWith("XWorker")
                || threadName.startsWith("XDirector")
                || threadName.startsWith("XStat")
                || threadName.startsWith("XUncommitter");
    }

    private static boolean isGenerationalZ(String threadName) {
        return threadName.startsWith("ZDriver")
                || threadName.startsWith("ZWorker")
                || threadName.startsWith("ZDirector")
                || threadName.startsWith("ZStat")
                || threadName.startsWith("ZUncommitter");
    }

    private static boolean isVmThread(EventThread thread) {
        String osThreadName = thread.name();
        return osThreadName != null && thread.javaId() == null && osThreadName.startsWith("VM Thread");
    }

    @Override
    public void applyFrame(EventFrame frame) {
        // Is the frame from the VM thread and is it a garbage collection frame?
        // It can be single-threaded GC - SerialGC
        if (resolvedType == StacktraceType.JVM && isGarbageCollectionFrame(frame)) {
            resolvedType = StacktraceType.JVM_GC;
        }

        // Already processed by resolving using the thread
        if (resolvedType != null) {
            return;
        }

        FrameType frameType = FrameType.fromCode(frame.type());
        if (isThreadNativeEntryFrame(frame, frameType)) {
            resolvedType = StacktraceType.JVM;
        }

        if (isUnknownJava == null) {
            isUnknownJava = isUnknownFrame(frame);

            if (isUnknownJava) {
                resolvedType = StacktraceType.UNKNOWN;
                return;
            }
        }

        if (frameType.isJavaFrame()) {
            resolvedType = StacktraceType.APPLICATION;
        }
    }

    private static boolean isGarbageCollectionFrame(EventFrame frame) {
        return frame.method().startsWith("VM_GenCollectForAllocation");
    }

    private static boolean isUnknownFrame(EventFrame frame) {
        String method = frame.method();
        return method.startsWith("unknown_Java")
                || method.startsWith("no_Java_frame")
                || method.startsWith("not_walkable_Java");
    }

    private static boolean isThreadNativeEntryFrame(EventFrame frame, FrameType frameType) {
        return frameType == FrameType.CPP && frame.method().startsWith("thread_native_entry");
    }

    @Override
    public StacktraceType resolve() {
        if (resolvedType != null) {
            return resolvedType;
        }
        return StacktraceType.NATIVE;
    }
}
