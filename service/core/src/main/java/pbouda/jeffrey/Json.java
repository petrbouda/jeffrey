package pbouda.jeffrey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ObjectNode createNode() {
        return MAPPER.createObjectNode();
    }
}
