package pbouda.jeffrey.repository.model;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.manager.GraphType;

public record GraphContent(
        String id, String name, Type eventType, GraphType graphType,
        boolean useThreadMode, boolean useWeight, JsonNode content) {
}
