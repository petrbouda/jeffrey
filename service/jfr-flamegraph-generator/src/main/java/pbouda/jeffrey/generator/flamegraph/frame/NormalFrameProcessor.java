package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.List;
import java.util.function.Predicate;

public class NormalFrameProcessor<T extends StackBasedRecord> extends SingleFrameProcessor<T> {

    private final Predicate<RecordedFrame> lambdaMatcher;

    public NormalFrameProcessor(Predicate<RecordedFrame> lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex) {
        return !lambdaMatcher.test(stacktrace.get(currIndex));
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
