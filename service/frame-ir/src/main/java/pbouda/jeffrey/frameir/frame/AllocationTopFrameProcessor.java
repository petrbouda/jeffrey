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

import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.FrameType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;

import java.util.List;

public class AllocationTopFrameProcessor extends SingleFrameProcessor {

    @Override
    public NewFrame processSingle(FlamegraphRecord record, JfrStackFrame currFrame, boolean topFrame) {
        FrameType currentFrameType;
        if (Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(record.type())) {
            currentFrameType = FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC;
        } else if (Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.sameAs(record.type())) {
            currentFrameType = FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC;
        } else {
            currentFrameType = FrameType.ALLOCATED_OBJECT_SYNTHETIC;
        }

        return new NewFrame(
                RecordedClassMapper.map(record.weightEntity().className()),
                currFrame.lineNumber(),
                currFrame.bytecodeIndex(),
                currentFrameType,
                true,
                record.samples(),
                record.weight());
    }

    @Override
    public boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        return currIndex == (stacktrace.size() - 1);
    }
}
