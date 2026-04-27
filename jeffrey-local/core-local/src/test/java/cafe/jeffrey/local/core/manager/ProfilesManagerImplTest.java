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

package cafe.jeffrey.local.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.local.persistence.api.LocalCoreRepositories;
import cafe.jeffrey.local.persistence.api.ProfileRepository;
import cafe.jeffrey.local.persistence.api.RecordingRepository;
import cafe.jeffrey.shared.common.model.*;
import cafe.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilesManagerImplTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            "proj-1", null, "Test Project", null, null,
            "ws-1", NOW, null, Map.of(), null);

    @Mock
    private LocalCoreRepositories localCoreRepositories;
    @Mock
    private ProfileInitializer profileInitializer;
    @Mock
    private RecordingRepository projectRecordingRepository;
    @Mock
    private ProjectRecordingStorage projectRecordingStorage;
    @Mock
    private ProfileManager.Factory profileManagerFactory;

    private ProfilesManagerImpl manager;

    @BeforeEach
    void setUp() {
        when(localCoreRepositories.newRecordingRepository("proj-1")).thenReturn(projectRecordingRepository);
        manager = new ProfilesManagerImpl(
                FIXED_CLOCK, PROJECT_INFO, localCoreRepositories,
                 projectRecordingStorage, profileManagerFactory, profileInitializer);
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

    @Nested
    class CreateProfile {

        @Test
        void throwsIllegalArgument_whenRecordingNotInDb() {
            when(projectRecordingRepository.findById("missing-rec")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> manager.createProfile("missing-rec"));
        }

        @Test
        void throwsIllegalArgument_whenRecordingFileNotInStorage() {
            Recording recording = new Recording(
                    "rec-1", "recording.jfr", "proj-1", null,
                    RecordingEventSource.JDK, NOW, NOW, NOW, false, null, null, List.of());

            when(projectRecordingRepository.findById("rec-1")).thenReturn(Optional.of(recording));
            when(projectRecordingStorage.findRecording("rec-1")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> manager.createProfile("rec-1"));
        }

        @Test
        void returnsCompletableFuture_whenRecordingAndFileExist() {
            Recording recording = new Recording(
                    "rec-1", "recording.jfr", "proj-1", null,
                    RecordingEventSource.JDK, NOW, NOW, NOW, false, null, null, List.of());
            java.nio.file.Path recordingPath = java.nio.file.Path.of("/recordings/rec-1/recording.jfr");

            when(projectRecordingRepository.findById("rec-1")).thenReturn(Optional.of(recording));
            when(projectRecordingStorage.findRecording("rec-1")).thenReturn(Optional.of(recordingPath));

            CompletableFuture<ProfileManager> future = manager.createProfile("rec-1");

            assertNotNull(future);
        }
    }
}
