package pbouda.jeffrey.jfrparser.jdk;

import pbouda.jeffrey.common.EventType;

import java.util.List;
import java.util.Objects;

public abstract class SingleEventProcessor implements EventProcessor {

    private final List<EventType> eventTypes;

    public SingleEventProcessor(EventType eventType) {
        Objects.requireNonNull(eventType);
        this.eventTypes = List.of(eventType);
    }

    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(eventTypes);
    }

    protected EventType eventType() {
        return eventTypes.getFirst();
    }
}
