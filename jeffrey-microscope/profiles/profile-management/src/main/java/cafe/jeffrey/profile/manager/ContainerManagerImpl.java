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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.manager.builder.ContainerConfigurationEventBuilder;
import cafe.jeffrey.profile.manager.builder.ContainerCpuThrottlingEventBuilder;
import cafe.jeffrey.profile.manager.model.container.ContainerConfigurationData;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.manager.model.container.ThrottlingSample;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

import java.util.List;

public class ContainerManagerImpl implements ContainerManager {

    private final ProfileEventStreamRepository eventStreamRepository;

    public ContainerManagerImpl(ProfileEventStreamRepository eventStreamRepository) {
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public ContainerConfigurationData configuration() {
        // The builder keeps the last streamed configuration ("latest wins"), so the stream must be
        // chronological — the events table is physically clustered, not guaranteed time-ordered
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.CONTAINER_CONFIGURATION))
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new ContainerConfigurationEventBuilder());
    }

    @Override
    public ContainerCpuThrottlingData throttling() {
        // Cumulative counters must be streamed in time order so the analyzer can delta consecutive samples.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.CONTAINER_CPU_THROTTLING))
                .withJsonFields()
                .orderedByTime();

        List<ThrottlingSample> samples =
                eventStreamRepository.genericStreaming(configurer, new ContainerCpuThrottlingEventBuilder());

        ContainerConfiguration config = configuration().configuration();
        return ContainerCpuThrottlingAnalyzer.analyze(samples, config);
    }
}
