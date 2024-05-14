package pbouda.jeffrey.jfrparser.jdk;

import pbouda.jeffrey.common.Type;

import java.util.List;

public class ProcessableEvents {

    private final boolean processableAll;

    private final List<String> eventNames;

    public ProcessableEvents(boolean processableAll) {
        this(processableAll, List.of());
    }

    public ProcessableEvents(List<Type> events) {
        this(false, events);
    }

    public static ProcessableEvents all() {
        return new ProcessableEvents(true);
    }

    private ProcessableEvents(boolean processableAll, List<Type> events) {
        this.processableAll = processableAll;
        this.eventNames = events.stream().map(Type::code).toList();
    }

    public boolean isProcessable(jdk.jfr.EventType eventType) {
        return processableAll || eventNames.contains(eventType.getName());
    }
}
