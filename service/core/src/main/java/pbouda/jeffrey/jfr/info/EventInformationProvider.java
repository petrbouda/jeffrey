package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfr.event.AllEventsProvider;
import pbouda.jeffrey.jfr.event.EventSummary;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<ArrayNode> {

    private final Path recording;
    private final CompositeExtraInfoEnhancer extraInfoEnhancer;
    private final List<Type> supportedEvents;

    public EventInformationProvider(Path recording) {
        this(recording, null);
    }

    public EventInformationProvider(Path recording, List<Type> supportedEvents) {
        this.recording = recording;
        this.extraInfoEnhancer = new CompositeExtraInfoEnhancer(recording);
        this.supportedEvents = supportedEvents;
        this.extraInfoEnhancer.initialize();
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
        List<EventSummary> events = new AllEventsProvider(recording, supportedEvents).get();
        ArrayNode arrayNode = Json.createArray();
        for (EventSummary event : events) {
            ObjectNode object = eventSummaryToJson(event);
            extraInfoEnhancer.accept(event.eventType(), object);
            arrayNode.add(object);
        }
        return arrayNode;
    }
}
