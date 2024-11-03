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

import java.util.ArrayList;
import java.util.List;

public class LambdaFrameProcessor<T extends StackBasedRecord> implements FrameProcessor<T> {

    private final LambdaMatcher lambdaMatcher;

    public LambdaFrameProcessor(LambdaMatcher lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(T record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        return lambdaMatcher.match(stacktrace, currIndex);
    }

    @Override
    public List<NewFrame> process(T record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        if (currIndex >= stacktrace.size()) {
            return List.of();
        }

        JfrStackFrame currFrame = stacktrace.get(currIndex);
        boolean isTopFrame = currIndex == (stacktrace.size() - 1);

        List<NewFrame> result = new ArrayList<>();
        if (LambdaMatchUtils.matchLambdaFrames(stacktrace, currIndex)) {
            result.add(createLambdaSynthetic(currFrame, record, isTopFrame));
            result.addAll(process(record, stacktrace, currIndex + 1));
        }

        return result;
    }

    private NewFrame createLambdaSynthetic(JfrStackFrame currFrame, T record, boolean isTopFrame) {
        return new NewFrame(
                "Lambda Frame (Synthetic)",
                currFrame.lineNumber(),
                currFrame.bytecodeIndex(),
                FrameType.LAMBDA_SYNTHETIC,
                isTopFrame,
                record.samples(),
                record.sampleWeight());
    }
}
