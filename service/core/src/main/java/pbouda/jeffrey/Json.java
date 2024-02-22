package pbouda.jeffrey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public abstract class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ArrayNode readArray(byte[] content) {
        try {
            return (ArrayNode) MAPPER.readTree(content);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse a content to a json array", e);
        }
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }
}
