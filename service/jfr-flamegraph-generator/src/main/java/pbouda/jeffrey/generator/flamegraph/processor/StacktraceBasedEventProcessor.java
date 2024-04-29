package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public abstract class StacktraceBasedEventProcessor<T extends StackBasedRecord>
        extends SingleEventProcessor implements Supplier<List<T>> {

    private final List<T> records = new ArrayList<>();
    private final Duration timeShift;
    private final AbsoluteTimeRange timeRange;

    public StacktraceBasedEventProcessor(EventType eventType, AbsoluteTimeRange absoluteTimeRange) {
        this(eventType, Duration.ZERO, absoluteTimeRange);
    }

    public StacktraceBasedEventProcessor(EventType eventType, Duration timeShift, AbsoluteTimeRange absoluteTimeRange) {
        super(eventType);
        this.timeShift = timeShift;
        this.timeRange = absoluteTimeRange;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

        // TimeShift to correlate 2 flamegraphs and different start-times
        eventTime = eventTime.plus(timeShift);

        if (eventTime.isBefore(timeRange.start()) || eventTime.isAfter(timeRange.end())) {
            return Result.CONTINUE;
        }

        records.add(mapEvent(event, eventTime));
        return Result.CONTINUE;
    }

    /**
     * Maps the {@link RecordedEvent} into the object for with all needed fields
     * from the event. It also provides {@code modifiedEventTime} because
     * the event's eventTime can be modified by the parent to e.g. correlate
     * two flamegraphs together.
     *
     * @param event             original recorded event
     * @param modifiedEventTime eventTime from the event that can be modified by the parent
     * @return mapped object with important fields from the event
     */
    abstract protected T mapEvent(RecordedEvent event, Instant modifiedEventTime);

    @Override
    public List<T> get() {
        records.sort(Comparator.comparing(T::timestamp));
        return records;
    }
}
