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

package pbouda.jeffrey.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.event.*;
import pbouda.jeffrey.common.model.EventTypeName;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.manager.model.gc.configuration.*;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;

public class GCConfigurationEventBuilder implements RecordBuilder<GenericRecord, GCConfigurationData> {

    private GCConfiguration gcConfiguration;
    private GCHeapConfiguration gcHeapConfiguration;
    private GCThreadConfiguration gcThreadConfiguration;
    private GCTLABConfiguration gcTlabConfiguration;
    private GCSurvivorConfiguration gcSurvivorConfiguration;
    private GCYoungGenerationConfiguration youngGenerationConfiguration;

    @Override
    public void onRecord(GenericRecord record) {
        String eventType = record.type().code();
        ObjectNode fields = record.jsonFields();

        switch (eventType) {
            case EventTypeName.GC_CONFIGURATION -> {
                gcConfiguration = Json.treeToValue(fields, GCConfiguration.class);
                gcThreadConfiguration = Json.treeToValue(fields, GCThreadConfiguration.class);
            }
            case EventTypeName.GC_HEAP_CONFIGURATION -> {
                gcHeapConfiguration = Json.treeToValue(fields, GCHeapConfiguration.class);
            }
            case EventTypeName.GC_TLAB_CONFIGURATION -> {
                gcTlabConfiguration = Json.treeToValue(fields, GCTLABConfiguration.class);
            }
            case EventTypeName.GC_SURVIVOR_CONFIGURATION ->  {
                gcSurvivorConfiguration = Json.treeToValue(fields, GCSurvivorConfiguration.class);
            }
            case EventTypeName.YOUNG_GENERATION_CONFIGURATION -> {
                youngGenerationConfiguration = Json.treeToValue(fields, GCYoungGenerationConfiguration.class);
            }
        }
    }

    @Override
    public GCConfigurationData build() {
        return new GCConfigurationData(
                GarbageCollectorType.fromOldGenCollector(gcConfiguration.oldCollector()),
                gcConfiguration,
                gcHeapConfiguration,
                gcThreadConfiguration,
                gcSurvivorConfiguration,
                gcTlabConfiguration,
                youngGenerationConfiguration
        );
    }
}
