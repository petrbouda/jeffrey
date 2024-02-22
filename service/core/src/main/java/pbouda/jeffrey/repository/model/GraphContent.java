package pbouda.jeffrey.repository.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.manager.GraphType;

public record GraphContent(String id, String name, GraphType graphType, ObjectNode content) {
}