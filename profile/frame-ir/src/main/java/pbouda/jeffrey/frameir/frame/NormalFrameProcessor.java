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

import pbouda.jeffrey.profile.common.model.FrameType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.provider.api.repository.model.FlamegraphRecord;

import java.util.List;

public class NormalFrameProcessor extends SingleFrameProcessor {

    private final FrameNameBuilder frameNameBuilder = new FrameNameBuilder();
    private final LambdaMatcher lambdaMatcher;
    private final boolean parseLocations;

    public NormalFrameProcessor(boolean parseLocations) {
        this(null, parseLocations);
    }

    public NormalFrameProcessor(LambdaMatcher lambdaMatcher, boolean parseLocations) {
        this.lambdaMatcher = lambdaMatcher;
        this.parseLocations = parseLocations;
    }

    @Override
    public boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        return lambdaMatcher == null || lambdaMatcher.doesNotMatch(stacktrace, currIndex);
    }

    @Override
    public NewFrame processSingle(FlamegraphRecord record, JfrStackFrame currFrame, boolean topFrame) {
        FrameType frameType = FrameType.fromCode(currFrame.type());
        return new NewFrame(
                frameNameBuilder.generateName(currFrame, record.thread(), frameType),
                parseLocations ? currFrame.lineNumber() : -1,
                parseLocations ? currFrame.bytecodeIndex() : -1,
                frameType,
                topFrame,
                record.samples(),
                record.weight());
    }
}
