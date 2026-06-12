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

import java.util.ArrayList;
import java.util.List;

public class LambdaFrameProcessor implements FrameProcessor {

    private static final String LAMBDA_FRAME_NAME = "Lambda Frame";

    private final LambdaMatcher lambdaMatcher;

    public LambdaFrameProcessor(LambdaMatcher lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        return lambdaMatcher.match(stacktrace, currIndex);
    }

    @Override
    public ProcessedFrames process(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        List<NewFrame> frames = collectLambdaFrames(record, stacktrace, currIndex);
        // Every emitted synthetic lambda frame replaces exactly one consumed stacktrace element.
        return new ProcessedFrames(frames, frames.size());
    }

    private List<NewFrame> collectLambdaFrames(
            FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {

        if (currIndex >= stacktrace.size()) {
            return List.of();
        }

        JfrStackFrame currFrame = stacktrace.get(currIndex);

        List<NewFrame> result = new ArrayList<>();
        if (LambdaMatchUtils.matchLambdaFrames(stacktrace, currIndex)) {
            result.add(createLambdaSynthetic(currFrame, record));
            result.addAll(collectLambdaFrames(record, stacktrace, currIndex + 1));
        }

        return result;
    }

    private NewFrame createLambdaSynthetic(JfrStackFrame currFrame, FlamegraphRecord record) {
        return new NewFrame(
                LAMBDA_FRAME_NAME,
                currFrame.lineNumber(),
                currFrame.bytecodeIndex(),
                FrameType.LAMBDA_SYNTHETIC,
                record.samples(),
                record.weight());
    }
}
