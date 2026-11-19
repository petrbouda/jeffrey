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

package cafe.jeffrey.microscope.core.profile.manager.builder;

import cafe.jeffrey.microscope.common.Json;
import cafe.jeffrey.microscope.core.profile.model.EventTypeName;
import cafe.jeffrey.microscope.core.profile.event.ContainerCpuThrottling;
import cafe.jeffrey.microscope.core.profile.manager.model.container.ThrottlingSample;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordBuilder;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects every {@code jdk.ContainerCPUThrottling} sample in time order (not "latest wins" — the
 * detector needs the whole series to delta the cumulative counters). Requires the query to be
 * {@code orderedByTime()}.
 */
public class ContainerCpuThrottlingEventBuilder implements RecordBuilder<GenericRecord, List<ThrottlingSample>> {

    private final List<ThrottlingSample> samples = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        if (!EventTypeName.CONTAINER_CPU_THROTTLING.equals(record.type().code())) {
            return;
        }
        ObjectNode fields = record.jsonFields();
        ContainerCpuThrottling counters = Json.treeToValue(fields, ContainerCpuThrottling.class);
        samples.add(new ThrottlingSample(record.timestampFromStart().toMillis(), counters));
    }

    @Override
    public List<ThrottlingSample> build() {
        return samples;
    }
}
