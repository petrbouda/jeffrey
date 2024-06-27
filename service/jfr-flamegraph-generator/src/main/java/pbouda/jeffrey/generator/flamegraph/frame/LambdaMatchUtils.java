package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;

import java.util.List;

public abstract class LambdaMatchUtils {

    public static final String DIRECT_METHOD_HANDLE_HOLDER_CLASS = "java.lang.invoke.DirectMethodHandle$Holder";
    public static final String LAMBDA_FORM_CLASS = "java.lang.invoke.LambdaForm$";
    public static final String LAMBDA_METHOD = "lambda$";

    public static boolean matchLambdaFrames(List<RecordedFrame> stacktrace, int currIndex) {
        RecordedFrame currFrame = stacktrace.get(currIndex);
        return isLambdaForm(currFrame)
                || isDirectMethodHandle(currFrame)
                || isLambdaClass(currFrame)
                || isLambdaMethod(currFrame);
    }

    /**
     * Parsing of:
     * ch.qos.logback.classic.joran.JoranConfigurator$$Lambda.0x00007fc6071135c0
     */
    private static boolean isLambdaClass(RecordedFrame frame) {
        String clazz = frame.getMethod().getType().getName();
        return clazz.contains("$$Lambda");
    }

    private static boolean isLambdaForm(RecordedFrame frame) {
        return frame.getMethod().getType().getName().startsWith(LAMBDA_FORM_CLASS);
    }

    private static boolean isDirectMethodHandle(RecordedFrame frame) {
        return frame.getMethod().getType().getName().startsWith(DIRECT_METHOD_HANDLE_HOLDER_CLASS);
    }

    private static boolean isLambdaMethod(RecordedFrame frame) {
        return frame.getMethod().getName().startsWith(LAMBDA_METHOD);
    }

    private static boolean isNextLambdaMethod(List<RecordedFrame> stacktrace, int currIndex) {
        int nextIndex = currIndex + 1;
        if (nextIndex >= stacktrace.size()) {
            return false;
        }
        return isLambdaMethod(stacktrace.get(nextIndex));
    }
}
