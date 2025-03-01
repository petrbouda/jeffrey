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
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.ProfileInitializerProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class ProfileInitializerManagerImpl implements ProfileInitializationManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerManagerImpl.class);

    private final ProjectDirs projectDirs;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileInitializerProvider profileInitializerProvider;
    private final ProfileRecordingInitializer.Factory profileRecordingInitializerFactory;

    public ProfileInitializerManagerImpl(
            ProjectDirs projectDirs,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializerProvider profileInitializerProvider,
            ProfileRecordingInitializer.Factory profileRecordingInitializerFactory) {

        this.projectDirs = projectDirs;
        this.profileManagerFactory = profileManagerFactory;
        this.profileInitializerProvider = profileInitializerProvider;
        this.profileRecordingInitializerFactory = profileRecordingInitializerFactory;
    }

    @Override
    public ProfileManager initialize(Path relativeRecordingPath) {
        String projectId = projectDirs.readInfo().id();
        String profileId = UUID.randomUUID().toString();

        ProfileDirs profileDirs = this.projectDirs.profile(profileId);
        Path profileDir = profileDirs.initialize();
        LOG.info("Profile's directory created: {}", profileDir);

        // Initializes the profile's recording - copying to the workspace
        Path absoluteOriginalRecordingPath = projectDirs.recordingsDir().resolve(relativeRecordingPath);
        ProfileRecordingInitializer recordingInitializer = profileRecordingInitializerFactory.apply(projectId);
        // Copies one or more recordings to the profile's directory
        List<Path> profileRecordings = recordingInitializer.initialize(profileId, absoluteOriginalRecordingPath);

        // Name derived from the relativeRecordingPath
        // It can be a part of Profile Creation in the future.
        String profileName = relativeRecordingPath.getFileName().toString().replace(".jfr", "");

        ProfileInitializer profileInitializer = profileInitializerProvider.newProfileInitializer();

        long start = System.nanoTime();
        ProfileInfo profileInfo = profileInitializer.newProfile(projectId, profileName, profileRecordings);
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: elapsed_ms={}", millis);

        return profileManagerFactory.apply(profileInfo);
    }
}
