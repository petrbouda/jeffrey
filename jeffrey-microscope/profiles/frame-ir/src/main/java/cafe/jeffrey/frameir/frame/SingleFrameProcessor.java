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
