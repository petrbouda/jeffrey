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

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.frameir.record.StackBasedRecord;

import java.util.List;

abstract class SingleFrameProcessor<T extends StackBasedRecord> implements FrameProcessor<T> {

    abstract NewFrame processSingle(T record, RecordedFrame frame, boolean topFrame);

    @Override
    public List<NewFrame> process(T record, List<RecordedFrame> stacktrace, int currIndex) {
        RecordedFrame currFrame = stacktrace.get(currIndex);
        boolean topFrame = currIndex == (stacktrace.size() - 1);
        return List.of(processSingle(record, currFrame, topFrame));
    }
}
