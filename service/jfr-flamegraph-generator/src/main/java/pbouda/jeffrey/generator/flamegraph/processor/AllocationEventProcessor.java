package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.record.AllocationRecord;

import java.time.Instant;
import java.util.List;

public class AllocationEventProcessor extends StacktraceBasedEventProcessor<AllocationRecord> {

    private final String allocationField;

    public AllocationEventProcessor(Type eventType, AbsoluteTimeRange absoluteTimeRange, String allocationField) {
        this(List.of(eventType), absoluteTimeRange, allocationField);
    }

    public AllocationEventProcessor(List<Type> eventType, AbsoluteTimeRange absoluteTimeRange, String allocationField) {
        super(eventType, absoluteTimeRange);
        this.allocationField = allocationField;
    }

    @Override
    protected AllocationRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        return new AllocationRecord(
                modifiedEventTime,
                event.getStackTrace(),
                event.getThread(),
                event.getClass("objectClass"),
                event.getEventType(),
                event.getLong(allocationField));
    }
}