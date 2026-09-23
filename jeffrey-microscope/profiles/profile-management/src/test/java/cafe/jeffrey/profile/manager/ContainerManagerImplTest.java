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

package cafe.jeffrey.profile.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.microscope.model.Type;
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
