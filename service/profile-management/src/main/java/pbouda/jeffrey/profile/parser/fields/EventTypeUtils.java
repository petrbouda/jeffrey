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

package pbouda.jeffrey.profile.parser.fields;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.*;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.Type;

import java.util.ArrayList;
import java.util.List;

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
