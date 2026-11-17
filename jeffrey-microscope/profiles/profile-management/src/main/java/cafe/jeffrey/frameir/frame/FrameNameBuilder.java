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

package cafe.jeffrey.frameir.frame;

import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrThread;

public class FrameNameBuilder {

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame     currently processed frame.
     * @param thread    thread that emitted the stacktrace.
     * @param frameType type of the current frame.
     * @return standard name of the current frame.
     */
    public String generateName(JfrStackFrame frame, JfrThread thread, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED -> {
                JfrClass jfrClass = frame.method().clazz();
                yield jfrClass.className() + "#" + frame.method().methodName();
            }
            case COLLAPSED_SYNTHETIC -> frame.method().clazz().className();
            case CPP, KERNEL, NATIVE -> frame.method().methodName();
            // UNKNOWN frames carry no language/tier info (every pprof frame is UNKNOWN); FrameNames
            // picks the class/method delimiter ('#' for Java-like, dotted '::' for C++). See FrameNames.
            case UNKNOWN -> FrameNames.joinUnknown(
                    frame.method().clazz().className(), frame.method().methodName());
            case THREAD_NAME_SYNTHETIC -> methodNameBasedThread(thread);
            default -> throw new IllegalStateException("Unexpected value: " + frameType);
        };
    }

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame  currently processed frame.
     * @param thread thread that emitted the stacktrace.
     * @return standard name of the current frame.
     */
    public String generateName(JfrStackFrame frame, JfrThread thread) {
        FrameType frameType = FrameType.fromCode(frame.type());
        return generateName(frame, thread, frameType);
    }

    public static String methodNameBasedThread(JfrThread thread) {
        String threadName;
        if (thread.javaThreadId() > 0) {
            threadName = thread.name() + " (" + thread.javaThreadId() + ")";
        } else {
            threadName = thread.name() + " (" + thread.osThreadId() + ")";
        }

        if (thread.isVirtual()) {
            return threadName + " (V)";
        } else {
            return threadName;
        }
    }
}
