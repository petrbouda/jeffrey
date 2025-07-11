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

package pbouda.jeffrey.feature.checker;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.feature.FeatureCheckResult;
import pbouda.jeffrey.feature.FeatureType;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;

import java.util.Map;
import java.util.Optional;

public class ContainerDashboardFeatureChecker implements FeatureChecker {

    private final ProfileEventRepository eventRepository;

    public ContainerDashboardFeatureChecker(ProfileEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public FeatureCheckResult check(Map<Type, EventSummary> eventSummaries) {
        Optional<GenericRecord> configurationOpt = eventRepository.latest(Type.CONTAINER_CPU_THROTTLING);
        if (configurationOpt.isEmpty()) {
            return FeatureCheckResult.disabled(FeatureType.CONTAINER_DASHBOARD);
        }
        GenericRecord record = configurationOpt.get();
        JsonNode elapsedSlices = record.jsonFields().get("cpuElapsedSlices");

        return !elapsedSlices.isNull() && elapsedSlices.asInt() > 0
                ? FeatureCheckResult.enabled(FeatureType.CONTAINER_DASHBOARD)
                : FeatureCheckResult.disabled(FeatureType.CONTAINER_DASHBOARD);
    }
}
