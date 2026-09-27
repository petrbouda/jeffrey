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

import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;

/**
 * The one open object an output record may carry, advertised as {@code {"type":"object"}} with no
 * properties listed. It is for a payload that is genuinely polymorphic — the arguments of a follow-up
 * call, a bag of figures whose names vary — and nowhere else: a shape that is known is a record.
 * <p>
 * Serialised as the plain object it wraps. The node is copied in and copied out, so a record holding
 * one stays immutable.
 */
public record McpJsonObject(ObjectNode value) {

    public McpJsonObject {
        Objects.requireNonNull(value, "value");
        value = value.deepCopy();
    }

    /**
     * An object of the given entries, each value serialised the way Jackson writes it, in the map's
     * iteration order.
     */
    public static McpJsonObject of(Map<String, ?> entries) {
        ObjectNode node = McpJson.createObject();
        entries.forEach((key, entry) -> node.set(key, McpJson.toTree(entry)));
        return new McpJsonObject(node);
    }

    @Override
    @JsonValue
    public ObjectNode value() {
        return value.deepCopy();
    }
}
