package pbouda.jeffrey.generator.timeseries;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jdk.jfr.consumer.RecordedEvent;
import org.eclipse.collections.api.tuple.primitive.LongLongPair;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

public class TimeseriesEventProcessor extends SingleEventProcessor implements Supplier<ArrayNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Instant startTime;
    private final Instant endTime;

    private final LongLongHashMap values = new LongLongHashMap();
    private final long timeShift;

    public TimeseriesEventProcessor(
            EventType eventType,
            Instant profilingStart,
            Duration start,
            Duration duration) {

        this(0, eventType, profilingStart, start, duration);
    }

    public TimeseriesEventProcessor(
            long timeShift,
            EventType eventType,
            Instant profilingStart,
            Duration start,
            Duration duration) {

        super(eventType);
        this.timeShift = timeShift;

        if (start != null && start.isPositive()) {
            this.startTime = profilingStart.plus(start);
        } else {
            this.startTime = profilingStart;
        }

        if (duration != null && duration.isPositive()) {
            this.endTime = startTime.plus(duration);
        } else {
            this.endTime = null;
        }
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Instant eventTime = event.getStartTime();

        // TimeShift to correlate 2 timeseries and different start-times 
        eventTime = eventTime.plusMillis(timeShift);

        // This event is before the start of the processing, skip it.
        // TODO: More sophisticated parsing using chunks? Skip when the chunk was created after the end-time?
        if (eventTime.isBefore(startTime)) {
            return Result.CONTINUE;
        }

        // This event is after the end of the processing, skip it.
        // We cannot finish the whole processing, the events are not sorted by time.
        // TODO: More sophisticated parsing using chunks? Skip when the chunk was created after the end-time?
        if (endTime != null && eventTime.isAfter(endTime)) {
            return Result.CONTINUE;
        }

        long minute = eventTime.truncatedTo(ChronoUnit.SECONDS)
                .toEpochMilli();

        values.updateValue(minute, 1, val -> val + 1);

        return Result.CONTINUE;
    }

    @Override
    public ArrayNode get() {
        ArrayNode result = MAPPER.createArrayNode();
        for (LongLongPair pair : values.keyValuesView()) {
            ArrayNode timeSamples = MAPPER.createArrayNode();
            timeSamples.add(pair.getOne());
            timeSamples.add(pair.getTwo());
            result.add(timeSamples);
        }
        return result;
    }
}
