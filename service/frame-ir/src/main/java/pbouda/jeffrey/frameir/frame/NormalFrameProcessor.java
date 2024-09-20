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

public class NormalFrameProcessor<T extends StackBasedRecord> extends SingleFrameProcessor<T> {

    private final LambdaMatcher lambdaMatcher;

    public NormalFrameProcessor(LambdaMatcher lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(T record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        return lambdaMatcher.doesNotMatch(stacktrace, currIndex);
    }

    @Override
    public NewFrame processSingle(T record, JfrStackFrame currFrame, boolean topFrame) {
        FrameType frameType = FrameType.fromCode(currFrame.frameType());

        return new NewFrame(
                FrameNameBuilder.generateName(currFrame, record.thread(), frameType),
                currFrame.lineNumber(),
                currFrame.bytecodeIndex(),
                frameType,
                topFrame,
                record.sampleWeight());
    }
}
