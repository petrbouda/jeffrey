package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;

import java.util.Map;

public class AllocationSamplesExtraInfo implements ExtraInfoEnhancer {

    private final Map<String, String> settings;

    public AllocationSamplesExtraInfo(Map<String, String> settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(eventType);
    }

    @Override
    public void accept(ObjectNode json) {
        ObjectNode extras = Json.createObject()
                .put("source", settings.get("source"));

        json.set("extras", extras);
    }
}
