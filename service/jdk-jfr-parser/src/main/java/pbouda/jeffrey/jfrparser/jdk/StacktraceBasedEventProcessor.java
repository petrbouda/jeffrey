package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.EventType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class StacktraceBasedEventProcessor extends SingleEventProcessor implements Supplier<List<StackBasedRecord>> {

    private final List<StackBasedRecord> records = new ArrayList<>();
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

        StackBasedRecord record;
        if (event.hasField("sampledThread")) {
            record = new StackBasedRecord(
                    eventTime,
                    event.getStackTrace(),
                    event.getThread("sampledThread"));
        } else {
            record = new StackBasedRecord(
                    eventTime,
                    event.getStackTrace(),
                    null);
        }

        records.add(record);
        return Result.CONTINUE;
    }

    @Override
    public List<StackBasedRecord> get() {
        records.sort(Comparator.comparing(StackBasedRecord::timestamp));
        return records;
    }
}
