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

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.event.ContainerConfiguration;
import pbouda.jeffrey.common.event.ContainerIOUsage;
import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.feature.FeatureCheckResult;
import pbouda.jeffrey.feature.FeatureType;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.Map;
import java.util.Optional;

public class ContainerDashboardFeatureChecker implements FeatureChecker {

    private final ProfileEventRepository eventRepository;

    public ContainerDashboardFeatureChecker(ProfileEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public FeatureCheckResult check(Map<Type, EventSummary> eventSummaries) {
        return containerIOIsUsed() && containerIsConfigured()
                ? FeatureCheckResult.enabled(FeatureType.CONTAINER_DASHBOARD)
                : FeatureCheckResult.disabled(FeatureType.CONTAINER_DASHBOARD);
    }

    private boolean containerIOIsUsed() {
        Optional<ObjectNode> configurationOpt = eventRepository.latestJsonFields(Type.CONTAINER_IO_USAGE);
        if (configurationOpt.isEmpty()) {
            return false;
        }
        ContainerIOUsage usage = Json.treeToValue(configurationOpt.get(), ContainerIOUsage.class);
        return usage.dataTransferred() != null || usage.serviceRequests() != null;
    }

    private boolean containerIsConfigured() {
        Optional<ObjectNode> configurationOpt = eventRepository.latestJsonFields(Type.CONTAINER_CONFIGURATION);
        if (configurationOpt.isEmpty()) {
            return false;
        }
        ContainerConfiguration container = Json.treeToValue(
                configurationOpt.get(), ContainerConfiguration.class);

        return container.cpuQuota() != null
                || container.cpuShares() != -1
                || container.memorySoftLimit() != 0
                || container.memoryLimit() != -1
                || container.swapMemoryLimit() != -1;
    }
}
