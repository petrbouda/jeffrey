package pbouda.jeffrey.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final ObjectWriter WRITER = MAPPER.writer().withDefaultPrettyPrinter();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ObjectWriter writer() {
        return WRITER;
    }

    public static ArrayNode readArray(byte[] content) {
        try {
            return (ArrayNode) MAPPER.readTree(content);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse a content to a json array", e);
        }
    }

    public static <T> T read(Path path, Class<T> clazz) {
        try {
            String content = Files.readString(path);
            return MAPPER.readValue(content, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode read(String content) {
        return read(content.getBytes(Charset.defaultCharset()));
    }

    public static JsonNode read(byte[] content) {
        try {
            return MAPPER.readTree(content);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse a content to a json array", e);
        }
    }

    public static String toPrettyString(Object obj) {
        try {
            return Json.writer().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }
}
