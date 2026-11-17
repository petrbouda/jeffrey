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

import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.List;

abstract class SingleFrameProcessor implements FrameProcessor {

    abstract NewFrame processSingle(FlamegraphRecord record, JfrStackFrame frame);

    /**
     * Number of stacktrace elements consumed by a single invocation of this processor. Regular processors
     * translate exactly one stacktrace element into one frame and return {@code 1}. Synthetic processors
     * (thread frame, allocated-object/blocking-object top frames) emit a frame without consuming any
     * stacktrace element and return {@code 0}.
     *
     * @return number of consumed stacktrace elements per invocation.
     */
    abstract int consumedStackFrames();

    @Override
    public ProcessedFrames process(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        JfrStackFrame currFrame = stacktrace.get(currIndex);
        return new ProcessedFrames(List.of(processSingle(record, currFrame)), consumedStackFrames());
    }
}
