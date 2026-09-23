/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.EventTypeWithFields;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;

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
        // Single batched query for all configuration event types instead of one scan per type
        Map<Type, EventTypeWithFields> fieldsByType = eventTypeRepository.singleFieldsByEventTypes(EVENT_TYPES);

        ObjectNode result = Json.createObject();
        for (Type eventType : EVENT_TYPES) {
            EventTypeWithFields fields = fieldsByType.get(eventType);
            if (fields != null) {
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
        originalContent.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue().asString();

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
