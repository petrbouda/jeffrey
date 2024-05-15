package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.record.BlockingRecord;

import java.time.Instant;

public class BlockingEventProcessor extends StacktraceBasedEventProcessor<BlockingRecord> {

    private final String blockingClassField;

    public BlockingEventProcessor(AbsoluteTimeRange absoluteTimeRange, Type eventType, String blockingClassField) {
        super(eventType, absoluteTimeRange);
        this.blockingClassField = blockingClassField;
    }

    @Override
    protected BlockingRecord mapEvent(RecordedEvent event, Instant modifiedEventTime) {
        return new BlockingRecord(
                modifiedEventTime,
                event.getStackTrace(),
                event.getThread(),
                event.getClass(blockingClassField),
                event.getDuration().toNanos());
    }
}
