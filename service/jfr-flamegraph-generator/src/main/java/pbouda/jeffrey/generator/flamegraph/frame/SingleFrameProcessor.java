package pbouda.jeffrey.generator.flamegraph.frame;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.List;

abstract class SingleFrameProcessor<T extends StackBasedRecord> implements FrameProcessor<T> {

    abstract NewFrame processSingle(T record, RecordedFrame frame, boolean topFrame);

    @Override
    public List<NewFrame> process(T record, List<RecordedFrame> stacktrace, int currIndex) {
        RecordedFrame currFrame = stacktrace.get(currIndex);
        boolean topFrame = currIndex == (stacktrace.size() - 1);
        return List.of(processSingle(record, currFrame, topFrame));
    }
}
