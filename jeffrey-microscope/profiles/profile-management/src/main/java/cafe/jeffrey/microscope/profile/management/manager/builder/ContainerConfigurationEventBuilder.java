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

package cafe.jeffrey.microscope.profile.management.manager.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.microscope.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.profile.management.manager.model.container.ContainerConfigurationData;
import cafe.jeffrey.microscope.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.microscope.profile.persistence.api.GenericRecord;

public class ContainerConfigurationEventBuilder implements RecordBuilder<GenericRecord, ContainerConfigurationData> {

    private ContainerConfiguration containerConfiguration;

    @Override
    public void onRecord(GenericRecord record) {
        String eventType = record.type().code();
        ObjectNode fields = record.jsonFields();

        if (EventTypeName.CONTAINER_CONFIGURATION.equals(eventType)) {
            containerConfiguration = Json.treeToValue(fields, ContainerConfiguration.class);
        }
    }

    @Override
    public ContainerConfigurationData build() {
        return new ContainerConfigurationData(containerConfiguration);
    }
}
