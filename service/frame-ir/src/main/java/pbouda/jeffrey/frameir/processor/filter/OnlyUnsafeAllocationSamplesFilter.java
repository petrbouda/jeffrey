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

package pbouda.jeffrey.frameir.processor.filter;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import pbouda.jeffrey.frameir.FrameType;

public class OnlyUnsafeAllocationSamplesFilter implements EventProcessorFilter {

    @Override
    public boolean test(RecordedEvent event) {
        RecordedFrame firstNativeFrame = findFirstNativeFrame(event.getStackTrace());
        if (firstNativeFrame == null) {
            return false;
        }
        return firstNativeFrame.getMethod().getName().startsWith("Unsafe_AllocateMemory");
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
