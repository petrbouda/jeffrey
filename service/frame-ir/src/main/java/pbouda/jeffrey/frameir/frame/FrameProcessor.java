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
import pbouda.jeffrey.frameir.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;

import java.util.List;

public interface FrameProcessor<T extends StackBasedRecord> {

    record NewFrame(
            String methodName,
            int lineNumber,
            int bytecodeIndex,
            FrameType frameType,
            boolean isTopFrame,
            long sampleWeight) {
    }

    /**
     * Returns {@code true} if it makes sense to apply this processor, or if it's safety to apply it.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return checks whether the processor can be used for the current frame.
     */
    boolean isApplicable(T record, List<? extends JfrStackFrame> stacktrace, int currIndex);

    /**
     * Processes the current frame. It designed to be able to look and process frame back and in advance.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return list of newly create frames that will be appended to the latest one.
     */
    List<NewFrame> process(T record, List<? extends JfrStackFrame> stacktrace, int currIndex);

    /**
     * Utility method that checks if the invocation is applicable, and then it executes it.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return list of newly create frames that will be appended to the latest one.
     */
    default List<NewFrame> checkAndProcess(T record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        if (isApplicable(record, stacktrace, currIndex)) {
            return process(record, stacktrace, currIndex);
        } else {
            return List.of();
        }
    }
}
