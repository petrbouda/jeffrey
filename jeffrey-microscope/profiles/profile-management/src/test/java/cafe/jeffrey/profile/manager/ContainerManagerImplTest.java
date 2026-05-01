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

package cafe.jeffrey.profile.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.manager.builder.ContainerConfigurationEventBuilder;
import cafe.jeffrey.profile.manager.model.container.ContainerConfigurationData;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContainerManagerImpl")
class ContainerManagerImplTest {

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    @Nested
    @DisplayName("configuration()")
    class Configuration {

        @Test
        @DisplayName("Delegates to eventStreamRepository with CONTAINER_CONFIGURATION type")
        void delegatesToRepository() {
            ContainerConfigurationData mockData = new ContainerConfigurationData(null);
            when(eventStreamRepository.genericStreaming(any(EventQueryConfigurer.class), any(RecordBuilder.class)))
                    .thenReturn(mockData);

            var manager = new ContainerManagerImpl(eventStreamRepository);
            ContainerConfigurationData result = manager.configuration();

            assertSame(mockData, result);
            verify(eventStreamRepository).genericStreaming(any(EventQueryConfigurer.class), any(ContainerConfigurationEventBuilder.class));
        }
    }
}
