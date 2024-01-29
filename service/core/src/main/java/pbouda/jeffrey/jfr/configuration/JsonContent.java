package pbouda.jeffrey.jfr.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record JsonContent(String name, ObjectNode content) {
}
