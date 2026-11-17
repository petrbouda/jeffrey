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

package cafe.jeffrey.otlpparser.mapping;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.profiles.v1development.KeyValueAndUnit;
import cafe.jeffrey.otlpparser.dictionary.OtlpDictionary;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves OTLP dictionary-encoded attributes ({@code attribute_indices} referencing
 * {@code KeyValueAndUnit} entries) into plain key/value maps and JSON nodes.
 */
public final class OtlpAttributes {

    private OtlpAttributes() {
    }

    /**
     * Resolves attribute indices into an insertion-ordered {@code key -> AnyValue} map. Null/unknown
     * indices and entries with a blank key are skipped.
     */
    public static Map<String, AnyValue> resolve(List<Integer> attributeIndices, OtlpDictionary dictionary) {
        Map<String, AnyValue> attributes = new LinkedHashMap<>();
        for (Integer index : attributeIndices) {
            KeyValueAndUnit attribute = dictionary.attribute(index);
            if (attribute == null) {
                continue;
            }
            String key = dictionary.string(attribute.getKeyStrindex());
            if (key.isBlank()) {
                continue;
            }
            attributes.put(key, attribute.getValue());
        }
        return attributes;
    }

    /**
     * @return the attribute rendered as a string, or {@code null} for unset values and non-scalar types
     */
    public static String stringValue(AnyValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.getValueCase()) {
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> Boolean.toString(value.getBoolValue());
            case INT_VALUE -> Long.toString(value.getIntValue());
            case DOUBLE_VALUE -> Double.toString(value.getDoubleValue());
            default -> null;
        };
    }

    /**
     * @return the attribute as a long, or {@code null} when the value is not an integer/parsable string
     */
    public static Long longValue(AnyValue value) {
        if (value == null) {
            return null;
        }
        if (value.getValueCase() == AnyValue.ValueCase.INT_VALUE) {
            return value.getIntValue();
        }
        if (value.getValueCase() == AnyValue.ValueCase.STRING_VALUE) {
            try {
                return Long.parseLong(value.getStringValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Writes a scalar attribute into the JSON fields node; non-scalar values are rendered via
     * {@code toString} of their protobuf representation to stay lossless enough for the Event Viewer.
     */
    public static void putJsonField(ObjectNode fields, String key, AnyValue value) {
        if (value == null) {
            return;
        }
        switch (value.getValueCase()) {
            case STRING_VALUE -> fields.put(key, value.getStringValue());
            case BOOL_VALUE -> fields.put(key, value.getBoolValue());
            case INT_VALUE -> fields.put(key, value.getIntValue());
            case DOUBLE_VALUE -> fields.put(key, value.getDoubleValue());
            case VALUE_NOT_SET -> {
            }
            default -> fields.put(key, value.toString());
        }
    }
}
