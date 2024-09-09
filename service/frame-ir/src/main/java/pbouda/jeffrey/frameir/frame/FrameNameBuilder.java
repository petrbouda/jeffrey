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

import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.frameir.FrameType;

public abstract class FrameNameBuilder {

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame     currently processed frame.
     * @param thread    thread for generating the name in thread-mode.
     * @param frameType type of the current frame.
     * @return standard name of the current frame.
     */
    public static String generateName(RecordedFrame frame, RecordedThread thread, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED ->
                    frame.getMethod().getType().getName() + "#" + frame.getMethod().getName();
            case CPP, KERNEL, NATIVE -> frame.getMethod().getName();
            case THREAD_NAME_SYNTHETIC -> methodNameBasedThread(thread);
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Frame occurred in JFR");
            default -> throw new IllegalStateException("Unexpected value: " + frameType);
        };
    }

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame     currently processed frame.
     * @return standard name of the current frame.
     */
    public static String generateName(RecordedFrame frame) {
        RecordedThread thread = null;
        if (frame.hasField("sampledThread")) {
            thread = frame.getValue("sampledThread");
        }
        FrameType frameType = FrameType.fromCode(frame.getType());
        return generateName(frame, thread, frameType);
    }

    private static String methodNameBasedThread(RecordedThread thread) {
        if (thread.getJavaThreadId() > 0) {
            return thread.getJavaName() + " (" + thread.getJavaThreadId() + ")";
        } else {
            return thread.getOSName() + " (" + thread.getId() + ")";
        }
    }
}
