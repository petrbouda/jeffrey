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
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.manager.action.ProfileDataInitializer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class ProfilesManagerImpl implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesManagerImpl.class);

    private final ProjectDirs projectDirs;
    private final ProfileInitializationManager profileInitializationManager;
    private final ProfileDataInitializer profileDataInitializer;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            ProjectDirs projectDirs,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializationManager profileInitializationManager,
            ProfileDataInitializer profileDataInitializer) {

        this.projectDirs = projectDirs;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializationManager = profileInitializationManager;
        this.profileDataInitializer = profileDataInitializer;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return projectDirs.allProfiles().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public ProfileManager createProfile(Path relativePath) {
        ProfileManager profileManager = profileInitializationManager.initialize(relativePath);
        // Initializes the profile's data, e.g., configuration, auto-analysis, sections, viewer, ...
        profileDataInitializer.initialize(profileManager);
        return profileManager;
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        ProfileDirs profileDirs = projectDirs.profile(profileId);
        return profileDirs.readInfo()
                .map(profileManagerFactory);
    }
}
