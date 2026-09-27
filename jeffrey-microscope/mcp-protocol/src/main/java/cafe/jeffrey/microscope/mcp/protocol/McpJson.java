/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The one mapper the protocol reads and writes with.
 * <p>
 * Configured exactly as the application's own mapper is, so what a server writes through this module is
 * byte for byte what it wrote before the protocol had a module of its own: Jackson's defaults, with a
 * missing or null JSON value read as a primitive's zero ({@code false}, {@code 0}) instead of refused.
 * A failure is rethrown unchecked, as that mapper does, so a caller that catches {@link RuntimeException}
 * sees the same thing.
 */
final class McpJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private McpJson() {
    }

    static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }

    static JsonNode toTree(Object content) {
        return MAPPER.valueToTree(content);
    }

    static String toString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] toByteArray(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new RuntimeException("Cannot convert object to a byte array: " + value, e);
        }
    }

    static JsonNode readTree(String content) {
        try {
            return MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
