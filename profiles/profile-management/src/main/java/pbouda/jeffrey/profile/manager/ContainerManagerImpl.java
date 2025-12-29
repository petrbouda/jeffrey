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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.profile.manager.builder.ContainerConfigurationEventBuilder;
import pbouda.jeffrey.profile.manager.model.container.ContainerConfigurationData;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;

import java.util.List;

public class ContainerManagerImpl implements ContainerManager {

    private final ProfileEventStreamRepository eventStreamRepository;

    public ContainerManagerImpl(ProfileEventStreamRepository eventStreamRepository) {
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public ContainerConfigurationData configuration() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.CONTAINER_CONFIGURATION))
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ContainerConfigurationEventBuilder());
    }
}
