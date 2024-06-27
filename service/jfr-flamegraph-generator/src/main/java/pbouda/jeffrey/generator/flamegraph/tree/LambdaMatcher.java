package pbouda.jeffrey.generator.flamegraph.tree;

import jdk.jfr.consumer.RecordedFrame;

import java.util.function.Predicate;

public class LambdaMatcher implements Predicate<RecordedFrame> {

    private static final String LAMBDA_FORM_CLASS = "java.lang.invoke.LambdaForm$";
    private static final String LAMBDA_METHOD = "lambda$";

    public static final Predicate<RecordedFrame> ALWAYS_FALSE =  _ -> false;

    @Override
    public boolean test(RecordedFrame frame) {
        return frame.getMethod().getType().getName().startsWith(LAMBDA_FORM_CLASS);
//        return frame.getMethod().getType().getName().startsWith(LAMBDA_FORM_CLASS)
//                || frame.getMethod().getName().startsWith(LAMBDA_METHOD);
    }
}
