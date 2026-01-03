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
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.platform.repository.ProjectRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ProfilesManagerImpl implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesManagerImpl.class);

    private final PlatformRepositories platformRepositories;
    private final ProjectRepository projectRepository;
    private final ProfileInitializer profileInitializer;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            PlatformRepositories platformRepositories,
            ProjectRepository projectRepository,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializer profileInitializer) {

        this.platformRepositories = platformRepositories;
        this.projectRepository = projectRepository;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializer = profileInitializer;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return projectRepository.findAllProfiles().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public CompletableFuture<ProfileManager> createProfile(String recordingId) {
        return CompletableFuture.supplyAsync(
                        () -> profileInitializer.initialize(recordingId),
                        Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Could not create profile for recording: recording_id={} message={}",
                            recordingId, ex.getMessage(), ex);
                    throw new RuntimeException("Could not create profile for recording: " + recordingId, ex);
                });
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return platformRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }
}
