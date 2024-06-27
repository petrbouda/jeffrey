package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;

import java.util.List;

public class LambdaMatcher {

    public static final LambdaMatcher ALWAYS_FALSE = new LambdaMatcher() {
        @Override
        public boolean match(List<RecordedFrame> stacktrace, Integer currIndex) {
            return false;
        }
    };

    public boolean match(List<RecordedFrame> stacktrace, Integer currIndex) {
        return LambdaMatchUtils.matchLambdaFrames(stacktrace, currIndex);
    }

    public boolean doesNotMatch(List<RecordedFrame> stacktrace, Integer currIndex) {
        return !match(stacktrace, currIndex);
    }
}
