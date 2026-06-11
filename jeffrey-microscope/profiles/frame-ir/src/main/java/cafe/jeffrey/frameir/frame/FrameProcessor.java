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

package cafe.jeffrey.frameir.frame;

import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.List;

public interface FrameProcessor {

    record NewFrame(
            String methodName,
            int lineNumber,
            int bytecodeIndex,
            FrameType frameType,
            long samples,
            long sampleWeight) {
    }

    /**
     * Result of a single processor invocation. It distinguishes between the frames <b>emitted</b> into
     * the frame tree and the number of stacktrace elements <b>consumed</b> from the original stacktrace.
     * The two are not necessarily equal: synthetic processors (thread frame, allocated-object/blocking-object
     * top frames) emit a frame without consuming any stacktrace element, while regular processors consume
     * exactly the elements they translate into frames.
     *
     * @param frames              newly created frames that will be appended to the latest one.
     * @param consumedStackFrames number of elements of the original stacktrace consumed by the processor.
     */
    record ProcessedFrames(List<NewFrame> frames, int consumedStackFrames) {

        private static final ProcessedFrames NONE = new ProcessedFrames(List.of(), 0);

        public static ProcessedFrames none() {
            return NONE;
        }
    }

    /**
     * Returns {@code true} if it makes sense to apply this processor, or if it's safety to apply it.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return checks whether the processor can be used for the current frame.
     */
    boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex);

    /**
     * Processes the current frame. It designed to be able to look and process frame back and in advance.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return newly created frames together with the number of consumed stacktrace elements.
     */
    ProcessedFrames process(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex);

    /**
     * Utility method that checks if the invocation is applicable, and then it executes it.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return newly created frames together with the number of consumed stacktrace elements.
     */
    default ProcessedFrames checkAndProcess(
            FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {

        if (isApplicable(record, stacktrace, currIndex)) {
            return process(record, stacktrace, currIndex);
        } else {
            return ProcessedFrames.none();
        }
    }
}
