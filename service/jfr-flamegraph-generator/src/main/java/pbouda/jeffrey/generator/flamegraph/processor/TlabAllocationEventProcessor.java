package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.flamegraph.record.TlabAllocationRecord;

import java.time.Instant;

public class TlabAllocationEventProcessor extends StacktraceBasedEventProcessor<TlabAllocationRecord> {

    private final String allocationField;

    public TlabAllocationEventProcessor(EventType eventType, AbsoluteTimeRange absoluteTimeRange, String allocationField) {
        super(eventType, absoluteTimeRange);
        this.allocationField = allocationField;
    }

    @Override
    protected TlabAllocationRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        return new TlabAllocationRecord(
                modifiedEventTime,
                event.getStackTrace(),
                event.getThread(),
                event.getClass("objectClass"),
                event.getLong(allocationField));
    }
}
