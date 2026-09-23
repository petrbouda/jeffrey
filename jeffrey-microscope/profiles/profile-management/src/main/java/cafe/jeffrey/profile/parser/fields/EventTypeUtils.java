/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.parser.fields;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import jdk.jfr.*;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.SpanConventionKeys;
import cafe.jeffrey.microscope.model.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class EventTypeUtils {

    public static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    public static JsonNode toColumns(EventType eventType) {
        List<ValueDescriptor> fields = eventType.getFields();

        List<ObjectNode> columns = new ArrayList<>();
        if (!fields.isEmpty()) {
            for (ValueDescriptor desc : fields) {
                if (!IGNORED_FIELDS.contains(desc.getName())) {
                    ObjectNode type = Json.createObject()
                            .put("field", desc.getName())
                            .put("header", desc.getLabel())
                            .put("type", getContentType(desc))
                            .put("description", desc.getDescription());
                    columns.add(type);
                }
            }

            // Add special (artificial) fields
            // add to 2nd position (after the "id" field)
            if (Type.ACTIVE_SETTING.code().equals(eventType.getName())) {
                ObjectNode type = Json.createObject()
                        .put("field", "label")
                        .put("header", "Event Label")
                        .putNull("type")
                        .putNull("description");
                columns.add(2, type);
            }
        }

        return Json.mapper().valueToTree(columns);
    }

    /**
     * The naming convention the event type declared for itself — its {@code @Span} template —
     * read out of the recording's own metadata and carried as extras, where the trace derivation
     * discovers it the same way span discovery finds a {@code spanId} column.
     * <p>
     * Matched by annotation type name rather than by class: the annotation lives in
     * {@code jeffrey-events}, which this module deliberately does not compile against — the
     * convention crosses between the two as recording metadata, never as a shared type. The value
     * is read defensively because it comes from an arbitrary recording; anything that is not the
     * expected {@code String} is left out, and the derivation treats an absent key as "declared
     * nothing".
     */
    public static Map<String, String> toExtras(EventType eventType) {
        Map<String, String> extras = new LinkedHashMap<>();
        for (AnnotationElement annotation : eventType.getAnnotationElements()) {
            if (SpanConventionKeys.SPAN_ANNOTATION.equals(annotation.getTypeName())) {
                putIfString(extras, SpanConventionKeys.EXTRAS_SPAN_NAME, annotation.getValue("value"));
            }
        }
        return extras;
    }

    private static void putIfString(Map<String, String> extras, String key, Object value) {
        if (value instanceof String s) {
            extras.put(key, s);
        }
    }

    public static String getContentType(ValueDescriptor desc) {
        boolean lowPriority = true;
        String resolvedType = null;

        for (AnnotationElement anno : desc.getAnnotationElements()) {
            for (AnnotationElement meta : anno.getAnnotationElements()) {
                if (meta.getTypeName().equals(ContentType.class.getName())) {
                    String contentType = anno.getTypeName();
                    if (contentType.equals(Unsigned.class.getTypeName()) && lowPriority) {
                        resolvedType = contentType;
                    } else if (lowPriority) {
                        lowPriority = false;
                        resolvedType = contentType;
                    }
                }
            }
        }

        return resolvedType;
    }
}
