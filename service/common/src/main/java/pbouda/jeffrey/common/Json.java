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

    public static byte[] toByteArray(JsonNode node) {
        try {
            return WRITER.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot convert JsonNode to byte array: " + node, e);
        }
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
