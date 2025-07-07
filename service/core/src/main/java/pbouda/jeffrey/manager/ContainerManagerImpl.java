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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.manager.builder.ContainerConfigurationEventBuilder;
import pbouda.jeffrey.manager.model.container.ContainerConfigurationData;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.List;

public class ContainerManagerImpl implements ContainerManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;

    public ContainerManagerImpl(ProfileInfo profileInfo, ProfileEventRepository eventRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
    }

    @Override
    public ContainerConfigurationData configuration() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.CONTAINER_CONFIGURATION))
                .withJsonFields();

        return eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(new ContainerConfigurationEventBuilder());
    }
}
