package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class LambdaFrameProcessor<T extends StackBasedRecord> implements FrameProcessor<T> {

    private static final String LAMBDA_FORM = "java.lang.invoke.LambdaForm$";
    private static final String DIRECT_METHOD_HANDLE_HOLDER = "java.lang.invoke.DirectMethodHandle$Holder";

    private final Predicate<RecordedFrame> lambdaMatcher;

    public LambdaFrameProcessor(Predicate<RecordedFrame> lambdaMatcher) {
        this.lambdaMatcher = lambdaMatcher;
    }

    @Override
    public boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex) {
        return lambdaMatcher.test(stacktrace.get(currIndex));
    }

    @Override
    public List<NewFrame> process(T record, List<RecordedFrame> stacktrace, int currIndex) {
        if (currIndex >= stacktrace.size()) {
            return List.of();
        }

        RecordedFrame currFrame = stacktrace.get(currIndex);
        List<NewFrame> result = new ArrayList<>();

        if (isLambdaForm(currFrame) || isDirectMethodHandle(currFrame)) {
            boolean isTopFrame = currIndex == (stacktrace.size() - 1);
            NewFrame frame = new NewFrame(
                    "Lambda Frame (Synthetic)",
                    currFrame.getLineNumber(),
                    currFrame.getBytecodeIndex(),
                    FrameType.LAMBDA_SYNTHETIC,
                    isTopFrame,
                    record.sampleWeight());

            result.add(frame);
            result.addAll(process(record, stacktrace, currIndex + 1));
        }

        return result;
    }

    private static boolean isLambdaForm(RecordedFrame frame) {
        return frame.getMethod().getType().getName().startsWith(LAMBDA_FORM);
    }

    private static boolean isDirectMethodHandle(RecordedFrame frame) {
        return frame.getMethod().getType().getName().startsWith(DIRECT_METHOD_HANDLE_HOLDER);
    }
}
