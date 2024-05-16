package pbouda.jeffrey.generator.flamegraph.processor;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public abstract class StacktraceBasedEventProcessor<T extends StackBasedRecord>
        implements EventProcessor, Supplier<List<T>> {

    private final List<T> records = new ArrayList<>();
    private final AbsoluteTimeRange timeRange;
    private final ProcessableEvents processableEvents;

    public StacktraceBasedEventProcessor(Type eventType, AbsoluteTimeRange absoluteTimeRange) {
        this(List.of(eventType), absoluteTimeRange);
    }

    public StacktraceBasedEventProcessor(List<Type> eventTypes, AbsoluteTimeRange absoluteTimeRange) {
        this.timeRange = absoluteTimeRange;
        this.processableEvents = new ProcessableEvents(eventTypes);
    }

    @Override
    public ProcessableEvents processableEvents() {
        return processableEvents;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

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
