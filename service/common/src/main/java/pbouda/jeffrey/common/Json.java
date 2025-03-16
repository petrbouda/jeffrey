/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ext.NioPathDeserializer;
import com.fasterxml.jackson.databind.ext.NioPathSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.common.serde.RelativeTimeRangeDeserializer;
import pbouda.jeffrey.common.serde.RelativeTimeRangeSerializer;
import pbouda.jeffrey.common.serde.TypeDeserializer;
import pbouda.jeffrey.common.serde.TypeSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Json {

    private static final TypeReference<HashMap<String, String>> STRING_MAP_TYPE =
            new TypeReference<HashMap<String, String>>() {
            };

    private static final TypeReference<ArrayList<String>> STRING_LIST_TYPE =
            new TypeReference<ArrayList<String>>() {
            };

    private static final SimpleModule CUSTOM_PATH_SERDE = new SimpleModule("PathSerde")
            .addSerializer(Path.class, new NioPathSerializer())
            .addDeserializer(Path.class, new NioPathDeserializer());

    private static final SimpleModule CUSTOM_TYPES_SERDE = new SimpleModule()
            .addSerializer(new TypeSerializer())
            .addSerializer(new RelativeTimeRangeSerializer())
            .addDeserializer(Type .class, new TypeDeserializer())
            .addDeserializer(RelativeTimeRange .class, new RelativeTimeRangeDeserializer());

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(CUSTOM_PATH_SERDE)
            .registerModule(CUSTOM_TYPES_SERDE)
            .registerModule(new JavaTimeModule());

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Reads a content of a file and maps it to a given class
     *
     * @param path  path to the file
     * @param clazz class to which the content should be mapped
     * @param <T>   type of the class
     * @return an instance of the given class
     * @throws IOException to handle FileNotFoundException (Profile is not found for the given project)
     */
    public static <T> T read(Path path, Class<T> clazz) throws IOException {
        String content = Files.readString(path);
        return MAPPER.readValue(content, clazz);
    }

    public static <T> T read(String content, Class<T> clazz) {
        try {
            return MAPPER.readValue(content, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T read(String content, TypeReference<T> type) {
        try {
            return MAPPER.readValue(content, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T treeToValue(JsonNode content, Class<T> type) {
        try {
            return MAPPER.treeToValue(content, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, String> toMap(String content) {
        try {
            return MAPPER.readValue(content, STRING_MAP_TYPE);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse a content to a map", e);
        }
    }

    public static List<String> toList(String content) {
        try {
            return MAPPER.readValue(content, STRING_LIST_TYPE);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse a content to a list", e);
        }
    }

    public static JsonNode toTree(Object content) {
        return MAPPER.valueToTree(content);
    }

    public static String toPrettyString(Object obj) {
        try {
            return mapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(Object obj) {
        try {
            return mapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode readTree(String obj) {
        try {
            return mapper().readTree(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode readTree(InputStream stream) {
        try {
            return mapper().readTree(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayNode readToArrayNode(String obj) {
        try {
            return (ArrayNode) mapper().readTree(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode readObjectNode(String obj) {
        try {
            return (ObjectNode) mapper().readTree(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toByteArray(Object node) {
        try {
            return mapper().writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot convert object to a byte array: " + node, e);
        }
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }
}
