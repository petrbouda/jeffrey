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

package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.repository.Repositories;

import java.time.Duration;

public class ProfileInitializerManagerImpl implements ProfileInitializationManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerManagerImpl.class);

    private final Repositories repositories;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileInitializer profileInitializer;
    private final ProfileDataInitializer profileDataInitializer;

    public ProfileInitializerManagerImpl(
            Repositories repositories,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializer profileInitializer,
            ProfileDataInitializer profileDataInitializer) {

        this.repositories = repositories;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializer = profileInitializer;
        this.profileDataInitializer = profileDataInitializer;
    }

    @Override
    public ProfileManager initialize(String recordingId) {
        long start = System.nanoTime();
        String newProfileId = profileInitializer.newProfile(recordingId);
        ProfileInfo profileInfo = repositories.newProfileRepository(newProfileId).find()
                .orElseThrow(() -> new RuntimeException("Could not find newly created profile: " + newProfileId));

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: profile_id={} elapsed_ms={}", profileInfo.id(), millis);

        ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

        // Initializes the profile's data, e.g., configuration, auto-analysis, sections, viewer, ...
        profileDataInitializer.initialize(profileManager);

        // Enable newly created profile in the database
        repositories.newProfileRepository(profileInfo.id())
                .enableProfile();

        LOG.info("Profile has been initialized and enabled: profile_id={} profile_name={}",
                profileInfo.id(), profileInfo.name());

        return profileManager;
    }
}
