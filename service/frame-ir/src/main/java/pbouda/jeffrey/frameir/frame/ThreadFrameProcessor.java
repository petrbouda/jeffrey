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
import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.frameir.record.StackBasedRecord;

import java.util.List;

public class ThreadFrameProcessor<T extends StackBasedRecord> extends SingleFrameProcessor<T> {

    // Guards that the processor can be invoked only once at the very beginning for every record.
    private T currentRecord = null;

    @Override
    public NewFrame processSingle(T record, RecordedFrame currFrame, boolean topFrame) {
        currentRecord = record;

        return new NewFrame(
                FrameProcessor.generateName(null, record.thread(), FrameType.THREAD_NAME_SYNTHETIC),
                0,
                0,
                FrameType.THREAD_NAME_SYNTHETIC,
                false,
                record.sampleWeight());
    }

    @Override
    public boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex) {
        return currentRecord != record && record.thread() != null;
    }
}