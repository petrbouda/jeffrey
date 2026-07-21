/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.profile.common.event.ContainerCpuThrottling;
import cafe.jeffrey.profile.manager.model.container.ThrottlingSample;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

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
