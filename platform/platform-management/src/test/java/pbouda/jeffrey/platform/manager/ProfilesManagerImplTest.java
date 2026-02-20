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

package pbouda.jeffrey.platform.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfileRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRepository;
import pbouda.jeffrey.shared.common.model.*;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilesManagerImplTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            "proj-1", null, "Test Project", null, null,
            "ws-1", WorkspaceType.SANDBOX, NOW, null, Map.of());

    @Mock
    private PlatformRepositories platformRepositories;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProfileInitializer profileInitializer;
    @Mock
    private ProjectRecordingRepository projectRecordingRepository;
    @Mock
    private ProjectRecordingStorage projectRecordingStorage;
    @Mock
    private ProfileManager.Factory profileManagerFactory;

    private ProfilesManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new ProfilesManagerImpl(
                FIXED_CLOCK, PROJECT_INFO, platformRepositories, projectRepository,
                projectRecordingRepository, projectRecordingStorage,
                profileManagerFactory, profileInitializer);
    }

    @Nested
    class AllProfiles {

        @Test
        void returnsEmptyList_whenNoProfiles() {
            when(projectRepository.findAllProfiles()).thenReturn(List.of());

            List<? extends ProfileManager> result = manager.allProfiles();

            assertTrue(result.isEmpty());
        }

        @Test
        void mapsProfilesToManagers_viaFactory() {
            ProfileInfo profile = new ProfileInfo(
                    "p-1", "proj-1", "ws-1", "Profile 1",
                    RecordingEventSource.JDK, NOW, NOW, NOW, true);
            ProfileManager mockManager = mock(ProfileManager.class);

            when(projectRepository.findAllProfiles()).thenReturn(List.of(profile));
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
                    RecordingEventSource.JDK, NOW, NOW, NOW, true);
            ProfileRepository profileRepo = mock(ProfileRepository.class);
            ProfileManager mockManager = mock(ProfileManager.class);

            when(platformRepositories.newProfileRepository("p-1")).thenReturn(profileRepo);
            when(profileRepo.find()).thenReturn(Optional.of(profile));
            when(profileManagerFactory.apply(profile)).thenReturn(mockManager);

            Optional<ProfileManager> result = manager.profile("p-1");

            assertTrue(result.isPresent());
            assertSame(mockManager, result.get());
        }

        @Test
        void returnsEmpty_whenProfileNotFound() {
            ProfileRepository profileRepo = mock(ProfileRepository.class);
            when(platformRepositories.newProfileRepository("missing")).thenReturn(profileRepo);
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

            var future = manager.createProfile("missing-rec");

            assertThrows(Exception.class, future::join);
        }

        @Test
        void throwsIllegalArgument_whenRecordingFileNotInStorage() {
            Recording recording = new Recording(
                    "rec-1", "recording.jfr", "proj-1", null,
                    RecordingEventSource.JDK, NOW, NOW, NOW, false, List.of());

            when(projectRecordingRepository.findById("rec-1")).thenReturn(Optional.of(recording));
            when(projectRecordingStorage.findRecording("rec-1")).thenReturn(Optional.empty());

            var future = manager.createProfile("rec-1");

            assertThrows(Exception.class, future::join);
        }
    }
}
