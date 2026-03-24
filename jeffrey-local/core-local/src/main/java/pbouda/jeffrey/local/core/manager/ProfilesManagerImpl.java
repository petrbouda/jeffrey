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

package pbouda.jeffrey.local.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.local.persistence.repository.ProfileRepository;
import pbouda.jeffrey.local.persistence.repository.RecordingRepository;
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
    private final LocalCoreRepositories localCoreRepositories;
    private final ProfileInitializer profileInitializer;
    private final RecordingRepository projectRecordingRepository;
    private final ProjectRecordingStorage projectRecordingStorage;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            LocalCoreRepositories localCoreRepositories,
            RecordingRepository projectRecordingRepository,
            ProjectRecordingStorage projectRecordingStorage,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializer profileInitializer) {
        this.clock = clock;
        this.projectInfo = projectInfo;
        this.localCoreRepositories = localCoreRepositories;
        this.projectRecordingRepository = projectRecordingRepository;
        this.projectRecordingStorage = projectRecordingStorage;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializer = profileInitializer;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return localCoreRepositories.findAllProfilesByProject(projectInfo.id()).stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public CompletableFuture<ProfileManager> createProfile(String recordingId) {
        Recording recording = projectRecordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recording not found in database: recording_id=" + recordingId));

        Path recordingPath = projectRecordingStorage.findRecording(recordingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recording file not found in storage: recording_id=" + recordingId
                        + " project_id=" + projectInfo.id()));

        LOG.info("Profile creation task submitted: recordingId={} projectId={} recordingPath={}",
                recordingId, projectInfo.id(), recordingPath);

        return CompletableFuture.supplyAsync(
                () -> createProfileInternal(recording, recordingPath), Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Could not create profile for recording: recording_id={} message={}",
                            recordingId, ex.getMessage(), ex);
                    throw new RuntimeException("Could not create profile for recording: " + recordingId, ex);
                });
    }

    private ProfileManager createProfileInternal(Recording recording, Path recordingPath) {
        LOG.debug("Asynchronous profile creation started: recordingId={} projectId={} thread={}",
                recording.id(), projectInfo.id(), Thread.currentThread());

        String profileId = IDGenerator.generate();
        Instant profileCreatedAt = clock.instant();

        // Create an empty profile to be able to see profile initialization progress
        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);

        var insertProfile = ProfileRepository.InsertProfile.projectProfile(
                projectInfo.id(),
                projectInfo.workspaceId(),
                recording.recordingName(),
                recording.eventSource(),
                profileCreatedAt,
                recording.id(),
                recording.recordingStartedAt(),
                recording.recordingFinishedAt());

        profileRepository.insert(insertProfile);

        ProfileInfo profileInfo = localCoreRepositories.newProfileRepository(profileId).find()
                .orElseThrow(() -> new RuntimeException("Could not find newly created profile: " + profileId));

        ProfileManager profileManager = profileInitializer.initialize(profileInfo, recording.id(), recordingPath);
        profileRepository.enableProfile(clock.instant());
        return profileManager;
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }
}
