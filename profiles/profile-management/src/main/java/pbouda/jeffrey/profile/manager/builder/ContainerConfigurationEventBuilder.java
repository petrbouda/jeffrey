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

package pbouda.jeffrey.profile.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.shared.Json;
import pbouda.jeffrey.shared.model.EventTypeName;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.profile.manager.model.container.ContainerConfigurationData;
import pbouda.jeffrey.profile.common.event.ContainerConfiguration;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;

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
