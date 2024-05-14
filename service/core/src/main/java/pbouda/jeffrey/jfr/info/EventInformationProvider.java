package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfr.event.AllEventsProvider;
import pbouda.jeffrey.jfr.event.EventSummary;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<ArrayNode> {

    private final Path recording;

    private final Map<String, Consumer<ObjectNode>> specialHandling;

    public EventInformationProvider(Path recording) {
        this.recording = recording;

        specialHandling = Map.ofEntries(
                Map.entry(Type.EXECUTION_SAMPLE.code(), new ExecutionSamplesExtraInfo(recording))
        );
    }

    private static ObjectNode eventSummaryToJson(EventSummary event) {
        return Json.createObject()
                .put("label", event.eventType().getLabel())
                .put("code", event.eventType().getName())
                .put("samples", event.samples())
                .put("weight", event.weight());
    }

    @Override
    public ArrayNode get() {
        List<EventSummary> events = new AllEventsProvider(recording).get();
        ArrayNode arrayNode = Json.createArray();
        for (EventSummary event : events) {
            ObjectNode object = eventSummaryToJson(event);

            // Add some specific info for the selected event types
            Consumer<ObjectNode> handler = specialHandling.get(event.eventType().getName());
            if (handler != null) {
                handler.accept(object);
            }

            arrayNode.add(object);
        }
        return arrayNode;
    }
}
