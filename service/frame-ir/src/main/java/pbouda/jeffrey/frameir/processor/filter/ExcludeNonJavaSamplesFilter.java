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

package pbouda.jeffrey.frameir.processor.filter;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import pbouda.jeffrey.common.model.profile.FrameType;

public class ExcludeNonJavaSamplesFilter implements EventProcessorFilter {

    @Override
    public boolean test(RecordedEvent event) {
        FrameType frameType = findTypeOfFirstNonNativeFrame(event.getStackTrace());
        return frameType != null && frameType.isJavaFrame();
    }

    private static FrameType findTypeOfFirstNonNativeFrame(RecordedStackTrace stackTrace) {
        for (RecordedFrame frame : stackTrace.getFrames().reversed()) {
            FrameType frameType = FrameType.fromCode(frame.getType());
            if (frameType != FrameType.NATIVE) {
                return frameType;
            }
        }
        return null;
    }
}
