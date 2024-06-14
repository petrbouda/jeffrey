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

package pbouda.jeffrey.generator.flamegraph.tree;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.BlockingRecord;

public class BlockingTreeBuilder extends FrameTreeBuilder<BlockingRecord> {

    public BlockingTreeBuilder(boolean threadMode) {
        super(true, threadMode);
    }

    @Override
    protected Frame specialTopFrame(BlockingRecord record, Frame parent, RecordedFrame current) {
        String objectClass = RecordedClassMapper.map(record.blockingClass());
        Frame resolvedFrame = parent.get(objectClass);
        if (resolvedFrame == null) {
            resolvedFrame = new Frame(
                    objectClass, current.getLineNumber(), current.getBytecodeIndex());
            parent.put(objectClass, resolvedFrame);
        }
        resolvedFrame.increment(FrameType.BLOCKING_OBJECT_SYNTHETIC, record.sampleWeight(), true);

        return resolvedFrame;
    }

/**
 * Highlights the constructors in the flamegraph to see "allocation frame"
 */
//    @Override
//    protected Frame addFrameToLayer(
//            String methodName,
//            int lineNumber,
//            int bytecodeIndex,
//            FrameType frameType,
//            boolean isTopFrame,
//            long sampleWeight,
//            Frame parent) {
//
//        return super.addFrameToLayer(
//                methodName,
//                lineNumber,
//                bytecodeIndex,
//                methodName.endsWith("#<init>") ? FrameType.KERNEL : frameType,
//                isTopFrame,
//                sampleWeight,
//                parent);
//    }
}
