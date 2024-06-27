package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.List;

public class ThreadFrameProcessor<T extends StackBasedRecord> extends SingleFrameProcessor<T> {

    // Guards that the processor can be invoked only once at the very beginning for every record.
    private T currentRecord = null;

    @Override
    public NewFrame processSingle(T record, RecordedFrame currFrame, boolean topFrame) {
        currentRecord = record;

        return new NewFrame(
                FrameProcessor.generateName(null, record.thread(), FrameType.THREAD_NAME_SYNTHETIC),
                0,
                0,
                FrameType.THREAD_NAME_SYNTHETIC,
                false,
                record.sampleWeight());
    }

    @Override
    public boolean isApplicable(T record, List<RecordedFrame> stacktrace, int currIndex) {
        return currentRecord != record && record.thread() != null;
    }
}
