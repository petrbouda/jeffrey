package pbouda.jeffrey.generator.flamegraph.tree;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.TlabAllocationRecord;

public class TlabAllocationTreeBuilder extends FrameTreeBuilder<TlabAllocationRecord> {

    public TlabAllocationTreeBuilder() {
        super(true);
    }

    @Override
    protected Frame specialTopFrame(TlabAllocationRecord record, Frame parent, RecordedFrame current) {
        String objectClass = RecordedClassMapper.map(record.allocatedClass());
        Frame resolvedFrame = parent.get(objectClass);
        if (resolvedFrame == null) {
            resolvedFrame = new Frame(
                    objectClass, current.getLineNumber(), current.getBytecodeIndex());
            parent.put(objectClass, resolvedFrame);
        }

        resolvedFrame.increment(FrameType.INLINED, record.sampleWeight(), true);
        return resolvedFrame;
    }

    @Override
    protected Frame addFrameToLayer(
            String methodName,
            int lineNumber,
            int bytecodeIndex,
            FrameType frameType,
            boolean isTopFrame,
            long sampleWeight,
            Frame parent) {

        return super.addFrameToLayer(
                methodName,
                lineNumber,
                bytecodeIndex,
                methodName.endsWith("#<init>") ? FrameType.KERNEL : frameType,
                isTopFrame,
                sampleWeight,
                parent);
    }
}
