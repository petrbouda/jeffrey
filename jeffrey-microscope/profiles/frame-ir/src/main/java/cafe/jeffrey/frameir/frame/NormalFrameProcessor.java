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

import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.List;

public class NormalFrameProcessor extends SingleFrameProcessor {

    private final FrameNameBuilder frameNameBuilder = new FrameNameBuilder();
    private final boolean parseLocations;

    public NormalFrameProcessor(boolean parseLocations) {
        this.parseLocations = parseLocations;
    }

    @Override
    public NewFrame processSingle(FlamegraphRecord record, JfrStackFrame currFrame) {
        FrameType frameType = FrameType.fromCode(currFrame.type());
        return new NewFrame(
                frameNameBuilder.generateName(currFrame, record.thread(), frameType),
                parseLocations ? currFrame.lineNumber() : -1,
                parseLocations ? currFrame.bytecodeIndex() : -1,
                frameType,
                record.samples(),
                record.weight(),
                currFrame.method().clazz().isHidden());
    }

    @Override
    int consumedStackFrames() {
        return 1;
    }
}
