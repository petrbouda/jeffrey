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

package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ProfilesManagerImpl implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesManagerImpl.class);

    private final Repositories repositories;
    private final ProjectRepository projectRepository;
    private final ProfileInitializationManager profileInitializationManager;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            Repositories repositories,
            ProjectRepository projectRepository,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializationManager profileInitializationManager) {

        this.repositories = repositories;
        this.projectRepository = projectRepository;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializationManager = profileInitializationManager;
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
                        () -> profileInitializationManager.initialize(recordingId),
                        Schedulers.sharedCached())
                .exceptionally(ex -> {
                    LOG.error("Could not create profile for recording: recording_id={} message={}",
                            recordingId, ex.getMessage(), ex);
                    throw new RuntimeException("Could not create profile for recording: " + recordingId, ex);
                });
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return repositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }
}
