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
