package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.ArrayList;
import java.util.List;

public class LambdaFrameProcessor<T extends StackBasedRecord> implements FrameProcessor<T> {

    private final LambdaMatcher lambdaMatcher;

    public LambdaFrameProcessor(LambdaMatcher lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex) {
        return lambdaMatcher.match(stacktrace, currIndex);
    }

    @Override
    public List<NewFrame> process(T record, List<RecordedFrame> stacktrace, int currIndex) {
        if (currIndex >= stacktrace.size()) {
            return List.of();
        }

        RecordedFrame currFrame = stacktrace.get(currIndex);
        boolean isTopFrame = currIndex == (stacktrace.size() - 1);

        List<NewFrame> result = new ArrayList<>();
        if (LambdaMatchUtils.matchLambdaFrames(stacktrace, currIndex)) {
            result.add(createLambdaSynthetic(currFrame, record, isTopFrame));
            result.addAll(process(record, stacktrace, currIndex + 1));
        }

        return result;
    }

    private NewFrame createLambdaSynthetic(RecordedFrame currFrame, T record, boolean isTopFrame) {
        return new NewFrame(
                "Lambda Frame (Synthetic)",
                currFrame.getLineNumber(),
                currFrame.getBytecodeIndex(),
                FrameType.LAMBDA_SYNTHETIC,
                isTopFrame,
                record.sampleWeight());
    }
}
