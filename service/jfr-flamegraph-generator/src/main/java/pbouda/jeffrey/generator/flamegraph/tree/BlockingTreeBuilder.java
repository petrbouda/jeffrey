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
