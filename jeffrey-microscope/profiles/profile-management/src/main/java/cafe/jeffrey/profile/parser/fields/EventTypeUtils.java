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

package cafe.jeffrey.profile.parser.fields;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import jdk.jfr.*;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.SpanConventionKeys;
import cafe.jeffrey.shared.common.model.Type;

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
     * The span conventions the event type declared for itself — a {@code @SpanName} template and a
     * {@code @SpanOutcome} field-and-semantics pair — read out of the recording's own metadata and
     * carried as extras, where the trace derivation discovers them the same way span discovery
     * finds a {@code spanId} column.
     * <p>
     * Matched by annotation type name rather than by class: the annotations live in
     * {@code jeffrey-events}, which this module deliberately does not compile against — the
     * convention crosses between the two as recording metadata, never as a shared type. The values
     * are read defensively because they come from an arbitrary recording; anything that is not the
     * expected {@code String} is left out, and the derivation treats an absent key as "declared
     * nothing".
     */
    public static Map<String, String> toExtras(EventType eventType) {
        Map<String, String> extras = new LinkedHashMap<>();
        for (AnnotationElement annotation : eventType.getAnnotationElements()) {
            if (SpanConventionKeys.SPAN_NAME_ANNOTATION.equals(annotation.getTypeName())) {
                putIfString(extras, SpanConventionKeys.EXTRAS_SPAN_NAME, annotation.getValue("value"));
            } else if (SpanConventionKeys.SPAN_OUTCOME_ANNOTATION.equals(annotation.getTypeName())) {
                putIfString(extras, SpanConventionKeys.EXTRAS_OUTCOME_FROM, annotation.getValue("from"));
                putIfString(extras, SpanConventionKeys.EXTRAS_OUTCOME_SEMANTICS, annotation.getValue("semantics"));
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
