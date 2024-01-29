package pbouda.jeffrey.jfr.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.util.List;
import java.util.function.Supplier;

public class JsonFieldEventProcessor extends SingleEventProcessor implements Supplier<JsonContent> {

    private static final List<String> IGNORED_FIELDS = List.of("eventThread", "duration", "startTime", "stackTrace");

    private JsonContent content = null;

    public JsonFieldEventProcessor(EventType eventType) {
        super(eventType);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ObjectNode node = Json.createNode();
        for (ValueDescriptor field : event.getFields()) {
            if (!IGNORED_FIELDS.contains(field.getName())) {
                Object value = event.getValue(field.getName());
                node.put(field.getLabel(), safeToString(value));
            }
        }
        this.content = new JsonContent(event.getEventType().getLabel(), node);
        return Result.DONE;
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    @Override
    public JsonContent get() {
        return content;
    }
}
