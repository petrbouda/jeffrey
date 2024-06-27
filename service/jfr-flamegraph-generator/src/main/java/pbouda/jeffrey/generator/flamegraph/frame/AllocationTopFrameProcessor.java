package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.AllocationRecord;

import java.util.List;

public class AllocationTopFrameProcessor extends SingleFrameProcessor<AllocationRecord> {

    @Override
    public NewFrame processSingle(AllocationRecord record, RecordedFrame currFrame, boolean topFrame) {
        FrameType currentFrameType;
        if (Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(record.eventType())) {
            currentFrameType = FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC;
        } else if (Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.sameAs(record.eventType())) {
            currentFrameType = FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC;
        } else {
            currentFrameType = FrameType.ALLOCATED_OBJECT_SYNTHETIC;
        }

        return new NewFrame(
                RecordedClassMapper.map(record.allocatedClass()),
                currFrame.getLineNumber(),
                currFrame.getBytecodeIndex(),
                currentFrameType,
                true,
                record.sampleWeight());
    }

    @Override
    public boolean isApplicable(AllocationRecord record, List<RecordedFrame> stacktrace, int currIndex) {
        return currIndex == (stacktrace.size() - 1);
    }
}
