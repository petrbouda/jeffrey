package pbouda.jeffrey.repository.model;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.manager.GraphType;

public record GraphContent(String id, String name, GraphType graphType, JsonNode content) {
}
