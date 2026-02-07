/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfileRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRepository;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ProfilesManagerImpl implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesManagerImpl.class);

    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final PlatformRepositories platformRepositories;
    private final ProjectRepository projectRepository;
    private final ProfileInitializer profileInitializer;
    private final ProjectRecordingRepository projectRecordingRepository;
    private final ProjectRecordingStorage projectRecordingStorage;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            PlatformRepositories platformRepositories,
            ProjectRepository projectRepository,
            ProjectRecordingRepository projectRecordingRepository,
            ProjectRecordingStorage projectRecordingStorage,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializer profileInitializer) {
        this.clock = clock;
        this.projectInfo = projectInfo;
        this.platformRepositories = platformRepositories;
        this.projectRepository = projectRepository;
        this.projectRecordingRepository = projectRecordingRepository;
        this.projectRecordingStorage = projectRecordingStorage;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializer = profileInitializer;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        LOG.debug("Listing all profiles: projectId={}", projectInfo.id());
        return projectRepository.findAllProfiles().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public CompletableFuture<ProfileManager> createProfile(String recordingId) {
        LOG.debug("Creating profile from recording: recordingId={} projectId={}", recordingId, projectInfo.id());
        return CompletableFuture.supplyAsync(() -> createProfileInternal(recordingId), Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Could not create profile for recording: recording_id={} message={}",
                            recordingId, ex.getMessage(), ex);
                    throw new RuntimeException("Could not create profile for recording: " + recordingId, ex);
                });
    }

    private ProfileManager createProfileInternal(String recordingId) {
        // --- Create profile from recording ---
        Recording recording = projectRecordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        Optional<Path> recordingPathOpt = projectRecordingStorage.findRecording(recordingId);
        if (recordingPathOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recording not found: recording_id=" + recordingId + " project_id=" + projectInfo.id());
        }

        String profileId = IDGenerator.generate();
        Instant profileCreatedAt = clock.instant();

        // Create an empty profile to be able to see profile initialization progress
        ProfileRepository profileRepository = platformRepositories.newProfileRepository(profileId);

        var insertProfile = new ProfileRepository.InsertProfile(
                projectInfo.id(),
                recording.recordingName(),
                recording.eventSource(),
                profileCreatedAt,
                recordingId,
                recording.recordingStartedAt(),
                recording.recordingFinishedAt());

        profileRepository.insert(insertProfile);

        ProfileInfo profileInfo = platformRepositories.newProfileRepository(profileId).find()
                .orElseThrow(() -> new RuntimeException("Could not find newly created profile: " + profileId));

        ProfileManager profileManager = profileInitializer.initialize(profileInfo, recordingId, recordingPathOpt.get());
        profileRepository.enableProfile(clock.instant());
        return profileManager;
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        LOG.debug("Looking up profile: profileId={} projectId={}", profileId, projectInfo.id());
        return platformRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }
}
