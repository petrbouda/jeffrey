package pbouda.jeffrey.generator.timeseries;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.EventType;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SearchableTimeseriesEventProcessor extends TimeseriesEventProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LongLongHashMap values = new LongLongHashMap();
    private final LongLongHashMap matchedValues = new LongLongHashMap();
    private final Predicate<String> searchPredicate;

    public SearchableTimeseriesEventProcessor(
            EventType eventType,
            AbsoluteTimeRange absoluteTimeRange,
            String searchPattern) {

        this(0, eventType, absoluteTimeRange, searchPattern);
    }

    public SearchableTimeseriesEventProcessor(
            long timeShift,
            EventType eventType,
            AbsoluteTimeRange absoluteTimeRange,
            String searchPattern) {

        super(timeShift, eventType, absoluteTimeRange);
        this.searchPredicate = Pattern.compile(".*" + searchPattern + ".*").asMatchPredicate();
    }

    @Override
    protected void incrementCounter(RecordedEvent event, long second) {
        if (matchesStacktrace(event.getStackTrace(), searchPredicate)) {
            matchedValues.addToValue(second, 1);
            values.getIfAbsentPut(second, 0);
        } else {
            values.addToValue(second, 1);
            matchedValues.getIfAbsentPut(second, 0);
        }
    }

    private static boolean matchesStacktrace(RecordedStackTrace stacktrace, Predicate<String> searchPredicate) {
        for (RecordedFrame frame : stacktrace.getFrames()) {
            if (matchesMethod(frame.getMethod(), searchPredicate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMethod(RecordedMethod method, Predicate<String> searchPredicate) {
        return searchPredicate.test(method.getName())
                || (method.getType() != null && searchPredicate.test(method.getType().getName()));
    }

    @Override
    public ArrayNode get() {
        ArrayNode values = buildResult(this.values);
        ArrayNode matchedValues = buildResult(this.matchedValues);
        return MAPPER.createArrayNode()
                .add(values)
                .add(matchedValues);
    }
}
