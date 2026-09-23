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

package cafe.jeffrey.microscope.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.microscope.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilesManagerImplTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            "proj-1", null, "Test Project", null, null,
            "ws-1", NOW, null, Map.of(), null);

    @Mock
    private MicroscopeCoreRepositories localCoreRepositories;
    @Mock
    private ProfileManager.Factory profileManagerFactory;

    private ProfilesManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new ProfilesManagerImpl(PROJECT_INFO, localCoreRepositories, profileManagerFactory);
    }

    @Nested
    class AllProfiles {

        @Test
        void returnsEmptyList_whenNoProfiles() {
            when(localCoreRepositories.findAllProfilesByProject("proj-1")).thenReturn(List.of());

            List<? extends ProfileManager> result = manager.allProfiles();

            assertTrue(result.isEmpty());
        }

        @Test
        void mapsProfilesToManagers_viaFactory() {
            ProfileInfo profile = new ProfileInfo(
                    "p-1", "proj-1", "ws-1", "Profile 1",
                    RecordingEventSource.JDK, NOW, NOW, NOW, true, false, "rec-1");
            ProfileManager mockManager = mock(ProfileManager.class);

            when(localCoreRepositories.findAllProfilesByProject("proj-1")).thenReturn(List.of(profile));
            when(profileManagerFactory.apply(profile)).thenReturn(mockManager);

            List<? extends ProfileManager> result = manager.allProfiles();

            assertEquals(1, result.size());
            assertSame(mockManager, result.getFirst());
        }
    }

    @Nested
    class FindProfile {

        @Test
        void returnsProfileManager_whenProfileExists() {
            ProfileInfo profile = new ProfileInfo(
                    "p-1", "proj-1", "ws-1", "Profile 1",
                    RecordingEventSource.JDK, NOW, NOW, NOW, true, false, "rec-1");
            ProfileRepository profileRepo = mock(ProfileRepository.class);
            ProfileManager mockManager = mock(ProfileManager.class);

            when(localCoreRepositories.newProfileRepository("p-1")).thenReturn(profileRepo);
            when(profileRepo.find()).thenReturn(Optional.of(profile));
            when(profileManagerFactory.apply(profile)).thenReturn(mockManager);

            Optional<ProfileManager> result = manager.profile("p-1");

            assertTrue(result.isPresent());
            assertSame(mockManager, result.get());
        }

        @Test
        void returnsEmpty_whenProfileNotFound() {
            ProfileRepository profileRepo = mock(ProfileRepository.class);
            when(localCoreRepositories.newProfileRepository("missing")).thenReturn(profileRepo);
            when(profileRepo.find()).thenReturn(Optional.empty());

            Optional<ProfileManager> result = manager.profile("missing");

            assertTrue(result.isEmpty());
        }
    }

}
