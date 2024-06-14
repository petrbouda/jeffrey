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
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.AllocationRecord;

public class AllocationTreeBuilder extends FrameTreeBuilder<AllocationRecord> {

    public AllocationTreeBuilder(boolean threadMode) {
        super(true, threadMode);
    }

    @Override
    protected Frame specialTopFrame(AllocationRecord record, Frame parent, RecordedFrame current) {
        FrameType currentFrameType;
        if (Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(record.eventType())) {
            currentFrameType = FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC;
        } else if (Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.sameAs(record.eventType())) {
            currentFrameType = FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC;
        } else {
            currentFrameType = FrameType.ALLOCATED_OBJECT_SYNTHETIC;
        }

        String objectClass = RecordedClassMapper.map(record.allocatedClass());
        Frame resolvedFrame = parent.get(objectClass);
        if (resolvedFrame == null || resolvedFrame.frameType() != currentFrameType) {
            resolvedFrame = new Frame(objectClass, current.getLineNumber(), current.getBytecodeIndex());
            parent.put(objectClass, resolvedFrame);
        }

        resolvedFrame.increment(currentFrameType, record.sampleWeight(), true);
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
