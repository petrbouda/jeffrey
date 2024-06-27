package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.BlockingRecord;

import java.util.List;

public class BlockingTopFrameProcessor extends SingleFrameProcessor<BlockingRecord> {

    @Override
    public NewFrame processSingle(BlockingRecord record, RecordedFrame currFrame, boolean topFrame) {
        return new NewFrame(
                RecordedClassMapper.map(record.blockingClass()),
                currFrame.getLineNumber(),
                currFrame.getBytecodeIndex(),
                FrameType.BLOCKING_OBJECT_SYNTHETIC,
                true,
                record.sampleWeight());
    }

    @Override
    public boolean isApplicable(BlockingRecord record, List<RecordedFrame> stacktrace, int currIndex) {
        return currIndex == (stacktrace.size() - 1);
    }
}
