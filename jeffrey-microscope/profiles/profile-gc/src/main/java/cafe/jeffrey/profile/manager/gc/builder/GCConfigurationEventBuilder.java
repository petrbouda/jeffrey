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

package cafe.jeffrey.profile.manager.gc.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.profile.common.event.*;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.profile.manager.model.gc.configuration.*;
import cafe.jeffrey.provider.profile.api.GenericRecord;

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
