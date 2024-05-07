package pbouda.jeffrey.jfrparser.jdk;

import pbouda.jeffrey.common.EventType;

import java.util.List;

public class ProcessableEvents {

    private final boolean processableAll;

    private final List<String> eventNames;

    public ProcessableEvents(boolean processableAll) {
        this(processableAll, List.of());
    }

    public ProcessableEvents(List<EventType> events) {
        this(false, events);
    }

    public static ProcessableEvents all() {
        return new ProcessableEvents(true);
    }

    private ProcessableEvents(boolean processableAll, List<EventType> events) {
        this.processableAll = processableAll;
        this.eventNames = events.stream().map(EventType::code).toList();
    }

    public boolean isProcessable(jdk.jfr.EventType eventType) {
        return processableAll || eventNames.contains(eventType.getName());
    }
}
