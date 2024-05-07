package pbouda.jeffrey.jfr.stacktrace;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class EventSupportStackTraceProcessor implements EventProcessor, Supplier<Set<EventType>> {

    private final Set<EventType> result = new HashSet<>();

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public EventProcessor.Result onEvent(RecordedEvent event) {
        EventType eventType = event.getEventType();
        if (!result.contains(eventType) && event.getStackTrace() != null) {
            result.add(eventType);
        }

        return Result.CONTINUE;
    }

    @Override
    public Set<EventType> get() {
        return result;
    }
}
