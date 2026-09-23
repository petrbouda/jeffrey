/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.parser.tag;

import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.microscope.model.StacktraceTag;

public class UnsafeAllocationStacktraceTagResolver implements StacktraceTagResolver {

    public static final String UNSAFE_ALLOCATE_MEMORY = "Unsafe_AllocateMemory";

    @Override
    public StacktraceTag apply(RecordedStackTrace stacktrace) {
        RecordedFrame firstNativeFrame = findFirstNativeFrame(stacktrace);
        if (firstNativeFrame != null) {
            boolean isUnsafeAlloc = firstNativeFrame.getMethod().getName().startsWith(UNSAFE_ALLOCATE_MEMORY);
            if (isUnsafeAlloc) {
                return StacktraceTag.UNSAFE_ALLOCATION;
            }
        }
        return null;
    }

    private static RecordedFrame findFirstNativeFrame(RecordedStackTrace stackTrace) {
        for (RecordedFrame frame : stackTrace.getFrames()) {
            FrameType frameType = FrameType.fromCode(frame.getType());
            // Iterate from the bottom to up the stack trace, ignore CPP and Kernel frames
            // return NULL if the Java frame is hit before the NATIVE one
            if (frameType == FrameType.NATIVE) {
                return frame;
            } else if (frameType.isJavaFrame()) {
                break;
            }
        }
        return null;
    }
}
