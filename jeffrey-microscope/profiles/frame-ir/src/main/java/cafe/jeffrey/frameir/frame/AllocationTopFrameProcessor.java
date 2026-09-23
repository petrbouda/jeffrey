/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.frameir.frame;

import cafe.jeffrey.microscope.model.RecordedClassMapper;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.List;

public class AllocationTopFrameProcessor extends SingleFrameProcessor {

    @Override
    public NewFrame processSingle(FlamegraphRecord record, JfrStackFrame currFrame) {
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
                record.samples(),
                record.weight());
    }

    @Override
    int consumedStackFrames() {
        // Emits a synthetic allocated-object frame below the real leaf without consuming any stacktrace element.
        return 0;
    }

    @Override
    public boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        // Only synthesize the allocated-object leaf when the record carries a weight entity (the allocated
        // class). OTLP/pprof allocation events have no per-sample class, so skip it there (as the blocking
        // processor already does) rather than dereferencing a null entity.
        return currIndex == (stacktrace.size() - 1) && record.weightEntity() != null;
    }
}
