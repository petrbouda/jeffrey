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
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.List;

public class BlockingTopFrameProcessor extends SingleFrameProcessor {

    @Override
    public NewFrame processSingle(FlamegraphRecord record, JfrStackFrame currFrame) {
        return new NewFrame(
                RecordedClassMapper.map(record.weightEntity().className()),
                currFrame.lineNumber(),
                currFrame.bytecodeIndex(),
                FrameType.BLOCKING_OBJECT_SYNTHETIC,
                record.samples(),
                record.weight());
    }

    @Override
    int consumedStackFrames() {
        // Emits a synthetic blocking-object frame below the real leaf without consuming any stacktrace element.
        return 0;
    }

    @Override
    public boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        return currIndex == (stacktrace.size() - 1) && record.weightEntity() != null;
    }
}
