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

package cafe.jeffrey.microscope.mcp.server;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The few JSON helpers this module's tests use, so they need no mapper from another Jeffrey module.
 * Jackson 3 throws unchecked exceptions, so each helper delegates straight to the mapper.
 */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }

    public static JsonNode toTree(Object content) {
        return MAPPER.valueToTree(content);
    }

    public static JsonNode readTree(String content) {
        return MAPPER.readTree(content);
    }

    public static String toString(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static byte[] toByteArray(Object value) {
        return MAPPER.writeValueAsBytes(value);
    }
}
