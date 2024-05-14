package pbouda.jeffrey.jfr.stacktrace;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;

public class StacktraceInfoProvider implements Supplier<ArrayNode> {

    private static final String STACKTRACE_FIELD_NAME = "jdk.types.StackTrace";

    private final Path recording;

    public StacktraceInfoProvider(Path recording) {
        this.recording = recording;
    }

    /**
     * [{
     * index: 0,
     * label: 'Execution Samples (CPU)',
     * code: 'jdk.ExecutionSample'
     * },{ ... }]
     */
    @Override
    public ArrayNode get() {
        Set<EventType> eventTypes = new RecordingFileIterator<>(
                recording, new EventSupportStackTraceProcessor())
                .collect();

        int index = 0;
        ArrayNode result = Json.createArray();
        for (EventType eventType : eventTypes) {
            if (containsStackTrace(eventType)) {
                result.add(createEventDescription(index++, eventType));
            }
        }

        return result;
    }

    private static ObjectNode createEventDescription(int i, EventType eventType) {
        return Json.createObject()
                .put("index", i)
                .put("label", eventType.getLabel())
                .put("code", eventType.getName());
    }


    private static boolean containsStackTrace(EventType eventType) {
        for (ValueDescriptor field : eventType.getFields()) {
            if (STACKTRACE_FIELD_NAME.equals(field.getTypeName())) {
                return true;
            }
        }
        return false;
    }
}
