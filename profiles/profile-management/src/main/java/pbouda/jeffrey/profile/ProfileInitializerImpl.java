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

package pbouda.jeffrey.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.profile.creator.ProfileCreator;
import pbouda.jeffrey.profile.manager.AdditionalFilesManager;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;

import java.time.Duration;

public class ProfileInitializerImpl implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerImpl.class);

    private final PlatformRepositories repositories;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileCreator profileCreator;
    private final ProfileDataInitializer profileDataInitializer;

    public ProfileInitializerImpl(
            PlatformRepositories repositories,
            ProfileManager.Factory profileManagerFactory,
            ProfileCreator profileCreator,
            ProfileDataInitializer profileDataInitializer) {

        this.repositories = repositories;
        this.profileManagerFactory = profileManagerFactory;
        this.profileCreator = profileCreator;
        this.profileDataInitializer = profileDataInitializer;
    }

    @Override
    public ProfileManager initialize(String recordingId) {
        long start = System.nanoTime();
        String newProfileId = profileCreator.createProfile(recordingId);
        ProfileInfo profileInfo = repositories.newProfileRepository(newProfileId).find()
                .orElseThrow(() -> new RuntimeException("Could not find newly created profile: " + newProfileId));

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: profile_id={} elapsed_ms={}", profileInfo.id(), millis);

        ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

        // Initializes the profile's data, e.g., configuration, auto-analysis, sections, viewer, ...
        profileDataInitializer.initialize(profileManager);

        // Processes data from the additional files if they exist
        AdditionalFilesManager additionalFilesManager = profileManager.additionalFilesManager();
        additionalFilesManager.processAdditionalFiles(recordingId);

        // Enable a newly created profile in the database
        repositories.newProfileRepository(profileInfo.id())
                .enableProfile();

        LOG.info("Profile has been initialized and enabled: profile_id={} profile_name={}",
                profileInfo.id(), profileInfo.name());

        return profileManager;
    }
}
