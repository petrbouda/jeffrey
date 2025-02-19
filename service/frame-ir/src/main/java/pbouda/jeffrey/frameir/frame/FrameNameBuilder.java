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

import pbouda.jeffrey.common.model.profile.FrameType;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

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
            case CPP, KERNEL, NATIVE -> frame.method().methodName();
            case THREAD_NAME_SYNTHETIC -> methodNameBasedThread(thread);
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Frame occurred in JFR");
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
        return generateName(frame, thread, frame.type());
    }

    public static String methodNameBasedThread(JfrThread thread) {
        if (thread.javaThreadId() > 0) {
            return thread.name() + " (" + thread.javaThreadId() + ")";
        } else {
            return thread.name() + " (" + thread.osThreadId() + ")";
        }
    }
}
