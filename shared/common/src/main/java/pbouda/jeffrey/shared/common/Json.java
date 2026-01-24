/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.common;

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
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.shared.common.serde.RelativeTimeRangeDeserializer;
import pbouda.jeffrey.shared.common.serde.RelativeTimeRangeSerializer;
import pbouda.jeffrey.shared.common.serde.TypeDeserializer;
import pbouda.jeffrey.shared.common.serde.TypeSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Json {

    public static final String EMPTY = "{}";

    private static final TypeReference<HashMap<String, String>> STRING_MAP_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<ArrayList<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final SimpleModule CUSTOM_PATH_SERDE = new SimpleModule("PathSerde")
            .addSerializer(Path.class, new NioPathSerializer())
            .addDeserializer(Path.class, new NioPathDeserializer());

    private static final SimpleModule CUSTOM_TYPES_SERDE = new SimpleModule()
            .addSerializer(new TypeSerializer())
            .addSerializer(new RelativeTimeRangeSerializer())
            .addDeserializer(Type.class, new TypeDeserializer())
            .addDeserializer(RelativeTimeRange.class, new RelativeTimeRangeDeserializer());

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(CUSTOM_PATH_SERDE)
            .registerModule(CUSTOM_TYPES_SERDE)
            .registerModule(new JavaTimeModule());

    public static ObjectMapper mapper() {
        return MAPPER;
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

    public static <T> T read(File file, TypeReference<T> type) {
        try {
            return MAPPER.readValue(file, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T read(Path path, Class<T> clazz) {
        try {
            return MAPPER.readValue(path.toFile(), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void write(Path path, Object value) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
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

    public static String toJson(Map<String, String> map) {
        return (map != null) ? Json.toString(map) : null;
    }

    public static String readString(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode == null || fieldNode.isNull() ? null : fieldNode.asText();
    }

    public static long readLong(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return -1L;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode == null || fieldNode.isNull() ? -1L : fieldNode.asLong();
    }

    public static int readInt(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return -1;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode == null || fieldNode.isNull() ? -1 : fieldNode.asInt();
    }

    public static boolean readBoolean(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return false;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() && fieldNode.asBoolean();
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }
}
