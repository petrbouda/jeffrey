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

package pbouda.jeffrey.profile.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.provider.profile.model.EventTypeWithFields;
import pbouda.jeffrey.provider.profile.repository.ProfileEventTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ProfileConfigurationManagerImpl implements ProfileConfigurationManager {

    private static final TypeReference<List<FieldNames>> FIELD_NAME_LIST =
            new TypeReference<List<FieldNames>>() {
            };

    private record FieldNames(String type, Map<String, String> fields) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProfileConfigurationManagerImpl.class);

    private static final List<FieldNames> EVENT_TYPE_FIELD_NAMES;

    static {
        List<FieldNames> fieldNames = FileSystemUtils.readJson(
                "classpath:additional-info/event-type-fields.json", FIELD_NAME_LIST);
        EVENT_TYPE_FIELD_NAMES = Objects.requireNonNullElseGet(fieldNames, List::of);
    }

    private static final List<Type> EVENT_TYPES = List.of(
            Type.JVM_INFORMATION,
            Type.CONTAINER_CONFIGURATION,
            Type.CPU_INFORMATION,
            Type.OS_INFORMATION,
            Type.GC_CONFIGURATION,
            Type.GC_HEAP_CONFIGURATION,
            Type.GC_SURVIVOR_CONFIGURATION,
            Type.GC_TLAB_CONFIGURATION,
            Type.YOUNG_GENERATION_CONFIGURATION,
            Type.COMPILER_CONFIGURATION,
            Type.VIRTUALIZATION_INFORMATION
    );

    private static final List<String> IGNORED_FIELDS = List.of("eventThread", "duration", "startTime", "stackTrace");

    private final ProfileEventTypeRepository eventTypeRepository;

    public ProfileConfigurationManagerImpl(ProfileEventTypeRepository eventTypeRepository) {
        this.eventTypeRepository = eventTypeRepository;
    }

    @Override
    public JsonNode configuration() {
        LOG.debug("Building profile configuration");
        ObjectNode result = Json.createObject();
        for (Type eventType : EVENT_TYPES) {
            Optional<EventTypeWithFields> eventTypeWithFields = eventTypeRepository.singleFieldsByEventType(eventType);
            if (eventTypeWithFields.isPresent()) {
                EventTypeWithFields fields = eventTypeWithFields.get();
                ObjectNode cleanedContent = fields.content().remove(IGNORED_FIELDS);
                result.set(fields.label(), mapNamesToEventFields(fields.name(), cleanedContent));
            }
        }
        return result;
    }

    /**
     * Maps the field pretty name/label to the field name in the event type
     * e.g. startTime -> Start Time
     *
     * @param type            the event type
     * @param originalContent the content of the event type fields
     * @return a new JSON node with mapped field names
     */
    private static JsonNode mapNamesToEventFields(String type, ObjectNode originalContent) {
        Optional<FieldNames> fieldNamesOpt = EVENT_TYPE_FIELD_NAMES.stream()
                .filter(fn -> fn.type().equals(type))
                .findFirst();

        if (fieldNamesOpt.isEmpty()) {
            LOG.warn("No field names found for event type: {}", type);
            return originalContent;
        }

        Map<String, String> fieldNames = fieldNamesOpt.get().fields;

        ObjectNode newContent = Json.createObject();
        originalContent.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue().asText();

            String newFieldName = fieldNames.get(fieldName);
            if (newFieldName != null) {
                newContent.put(newFieldName, fieldValue);
            } else {
                newContent.put(fieldName, fieldValue);
                LOG.warn("Field name mapping not found: event_type={} field_name={}", type, fieldName);
            }
        });
        return newContent;
    }
}
