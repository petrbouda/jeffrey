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

package cafe.jeffrey.profile.feature.checker;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.common.event.ContainerIOUsage;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.feature.FeatureCheckResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;

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
