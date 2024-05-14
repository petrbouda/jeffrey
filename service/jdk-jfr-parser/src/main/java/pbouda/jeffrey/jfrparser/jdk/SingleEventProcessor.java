package pbouda.jeffrey.jfrparser.jdk;

import pbouda.jeffrey.common.Type;

import java.util.List;
import java.util.Objects;

public abstract class SingleEventProcessor implements EventProcessor {

    private final List<Type> eventTypes;

    public SingleEventProcessor(Type eventType) {
        Objects.requireNonNull(eventType);
        this.eventTypes = List.of(eventType);
    }

    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(eventTypes);
    }

    protected Type eventType() {
        return eventTypes.getFirst();
    }
}
