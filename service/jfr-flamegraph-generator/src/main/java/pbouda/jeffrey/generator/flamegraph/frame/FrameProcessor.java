package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.List;

public interface FrameProcessor<T extends StackBasedRecord> {

    record NewFrame(
            String methodName,
            int lineNumber,
            int bytecodeIndex,
            FrameType frameType,
            boolean isTopFrame,
            long sampleWeight) {
    }

    /**
     * Returns {@code true} if it makes sense to apply this processor, or if it's safety to apply it.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return checks whether the processor can be used for the current frame.
     */
    boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex);

    /**
     * Processes the current frame. It designed to be able to look and process frame back and in advance.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return list of newly create frames that will be appended to the latest one.
     */
    List<NewFrame> process(T record, List<RecordedFrame> stacktrace, int currIndex);

    /**
     * Utility method that checks if the invocation is applicable, and then it executes it.
     *
     * @param record     a record which is being currently processed.
     * @param stacktrace all frames in the stacktrace where the current frame belongs to.
     * @param currIndex  an index in the stacktrace belonging to the current frame.
     * @return list of newly create frames that will be appended to the latest one.
     */
    default List<NewFrame> checkAndProcess(T record, List<RecordedFrame> stacktrace, int currIndex) {
        if (isApplicable(record, stacktrace, currIndex)) {
            return process(record, stacktrace, currIndex);
        } else {
            return List.of();
        }
    }

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param frame     currently processed frame.
     * @param thread    thread for generating the name in thread-mode.
     * @param frameType type of the current frame.
     * @return standard name of the current frame.
     */
    static String generateName(RecordedFrame frame, RecordedThread thread, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED ->
                    frame.getMethod().getType().getName() + "#" + frame.getMethod().getName();
            case CPP, KERNEL, NATIVE -> frame.getMethod().getName();
            case THREAD_NAME_SYNTHETIC -> methodNameBasedThread(thread);
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Frame occurred in JFR");
            default -> throw new IllegalStateException("Unexpected value: " + frameType);
        };
    }

    private static String methodNameBasedThread(RecordedThread thread) {
        if (thread.getJavaThreadId() > 0) {
            return thread.getJavaName() + " (" + thread.getJavaThreadId() + ")";
        } else {
            return thread.getOSName() + " (" + thread.getId() + ")";
        }
    }
}
