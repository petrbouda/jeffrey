/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

public abstract class FrameNameBuilder {

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame     currently processed frame.
     * @param thread    thread for generating the name in thread-mode.
     * @param frameType type of the current frame.
     * @return standard name of the current frame.
     */
    public static String generateName(JfrStackFrame frame, JfrThread thread, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED ->
                    frame.method().clazz().name() + "#" + frame.method().name();
            case CPP, KERNEL, NATIVE -> frame.method().name();
            case THREAD_NAME_SYNTHETIC -> methodNameBasedThread(thread);
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Frame occurred in JFR");
            default -> throw new IllegalStateException("Unexpected value: " + frameType);
        };
    }

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame currently processed frame.
     * @param thread thread that emitted the stacktrace.
     * @return standard name of the current frame.
     */
    public static String generateName(JfrStackFrame frame, JfrThread thread) {
        FrameType frameType = FrameType.fromCode(frame.frameType());
        return generateName(frame, thread, frameType);
    }

    private static String methodNameBasedThread(JfrThread thread) {
        if (thread.javaThreadId() > 0) {
            return thread.javaName() + " (" + thread.javaThreadId() + ")";
        } else {
            return thread.osName() + " (" + thread.osThreadId() + ")";
        }
    }
}
