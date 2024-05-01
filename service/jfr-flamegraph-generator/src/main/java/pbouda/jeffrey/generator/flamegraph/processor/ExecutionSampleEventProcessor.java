package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.flamegraph.record.ExecutionSampleRecord;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.time.Instant;

public class ExecutionSampleEventProcessor extends StacktraceBasedEventProcessor<StackBasedRecord> {

    public ExecutionSampleEventProcessor(EventType eventType, AbsoluteTimeRange absoluteTimeRange) {
        super(eventType, absoluteTimeRange);
    }

    @Override
    protected ExecutionSampleRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        if (event.hasField("sampledThread")) {
            return new ExecutionSampleRecord(
                    modifiedEventTime,
                    event.getStackTrace(),
                    event.getThread());
        } else {
            return new ExecutionSampleRecord(
                    modifiedEventTime,
                    event.getStackTrace(),
                    null);
        }
    }
}
