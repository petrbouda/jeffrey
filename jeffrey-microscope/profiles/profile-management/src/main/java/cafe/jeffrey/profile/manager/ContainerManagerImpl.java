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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.microscope.model.Type;
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
