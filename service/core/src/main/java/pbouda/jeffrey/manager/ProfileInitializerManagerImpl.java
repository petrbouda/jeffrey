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
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.ProfileInitializerProvider;

import java.nio.file.Path;
import java.time.Duration;

public class ProfileInitializerManagerImpl implements ProfileInitializationManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerManagerImpl.class);

    private final ProjectDirs projectDirs;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileInitializerProvider profileInitializerProvider;

    public ProfileInitializerManagerImpl(
            ProjectDirs projectDirs,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializerProvider profileInitializerProvider) {

        this.projectDirs = projectDirs;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializerProvider = profileInitializerProvider;
    }

    @Override
    public ProfileManager initialize(Path relativeRecordingPath) {
        String projectId = projectDirs.readInfo().id();

        // Initializes the profile's recording - copying to the workspace
        Path originalRecordingPath = projectDirs.recordingsDir().resolve(relativeRecordingPath);

        ProfileInitializer profileInitializer = profileInitializerProvider.newProfileInitializer();

        long start = System.nanoTime();
        ProfileInfo profileInfo = profileInitializer.newProfile(projectId, originalRecordingPath);
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: elapsed_ms={}", millis);

        return profileManagerFactory.apply(profileInfo);
    }
}
