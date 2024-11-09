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
import pbouda.jeffrey.FlywayMigration;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.generator.basic.ProfilingStartTimeProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.manager.action.ProfilePostCreateAction;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.common.model.ProfileInfo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProfilesManagerImpl implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilesManagerImpl.class);

    private final ProjectDirs projectDirs;
    private final ProfilePostCreateAction postCreateAction;
    private final ProfileRecordingInitializer recordingInitializer;
    private final ProfileManager.Factory profileManagerFactory;

    public ProfilesManagerImpl(
            ProjectDirs projectDirs,
            ProfileManager.Factory profileManagerFactory,
            ProfilePostCreateAction postCreateAction,
            ProfileRecordingInitializer recordingInitializer) {

        this.profileManagerFactory = profileManagerFactory;
        this.projectDirs = projectDirs;
        this.postCreateAction = postCreateAction;
        this.recordingInitializer = recordingInitializer;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return projectDirs.allProfiles().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public ProfileManager createProfile(Path relativePath, boolean postCreateActions) {
        String profileId = UUID.randomUUID().toString();

        ProfileDirs profileDirs = this.projectDirs.profile(profileId);
        Path profileDir = profileDirs.initialize();
        LOG.info("Profile's directory created: {}", profileDir);

        Path absoluteOriginalRecordingPath = projectDirs.recordingsDir().resolve(relativePath);

        // Name derived from the recording
        // It can be a part of Profile Creation in the future.
        String profileName = relativePath.getFileName().toString().replace(".jfr", "");

        var profilingStartTimeOpt = JdkRecordingIterators.singleAndCollectIdentical(
                absoluteOriginalRecordingPath, new ProfilingStartTimeProcessor());
        if (profilingStartTimeOpt.isEmpty()) {
            throw new IllegalArgumentException("The recording does not contain: " + EventTypeName.ACTIVE_RECORDING);
        }

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, projectDirs.readInfo().id(),
                profileName, relativePath.toString(), Instant.now(), profilingStartTimeOpt.get());

        // Initializes the profile's recording - copying to the workspace
        recordingInitializer.initialize(profileId, absoluteOriginalRecordingPath);

        Path profileInfoPath = profileDirs.saveInfo(profileInfo);
        LOG.info("New profile's info generated: profile_info={}", profileInfoPath);

        FlywayMigration.migrate(profileDirs);
        LOG.info("Schema migrated to the new database file: {}", profileDirs.database());

        ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

        // Execute Post-create operation: pre-generate data and structures
        if (postCreateActions) {
            postCreateAction.execute(profileManager);
        }

        return profileManager;
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        ProfileDirs profileDirs = projectDirs.profile(profileId);
        return Optional.ofNullable(profileDirs.readInfo())
                .map(profileManagerFactory);
    }
}
