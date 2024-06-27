package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.List;

public class NormalFrameProcessor<T extends StackBasedRecord> extends SingleFrameProcessor<T> {

    private final LambdaMatcher lambdaMatcher;

    public NormalFrameProcessor(LambdaMatcher lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex) {
        return lambdaMatcher.doesNotMatch(stacktrace, currIndex);
    }

    @Override
    public NewFrame processSingle(T record, RecordedFrame currFrame, boolean topFrame) {
        FrameType frameType = FrameType.fromCode(currFrame.getType());

        return new NewFrame(
                FrameProcessor.generateName(currFrame, record.thread(), frameType),
                currFrame.getLineNumber(),
                currFrame.getBytecodeIndex(),
                frameType,
                topFrame,
                record.sampleWeight());
    }
}
