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
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.jfr.ProfilingStartTimeProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.manager.action.ProfilePostCreateAction;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DbBasedProfilesManager implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(DbBasedProfilesManager.class);

    private final WorkingDirs workingDirs;
    private final ProfilePostCreateAction postCreateAction;
    private final ProfileManager.Factory profileManagerFactory;

    public DbBasedProfilesManager(
            ProfileManager.Factory profileManagerFactory,
            WorkingDirs workingDirs,
            ProfilePostCreateAction postCreateAction) {

        this.profileManagerFactory = profileManagerFactory;
        this.workingDirs = workingDirs;
        this.postCreateAction = postCreateAction;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return workingDirs.retrieveAllProfiles().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public ProfileManager createProfile(Path recordingPath, boolean postCreateActions) {
        String profileId = UUID.randomUUID().toString();

        Path profileDir = workingDirs.createProfileHierarchy(profileId);
        LOG.info("Profile's directory created: {}", profileDir);

        Path fullRecordingPath = workingDirs.copyRecording(profileId, recordingPath);
        LOG.info("Recording copied to the profile's directory: {}", fullRecordingPath);

        // Name derived from the recording
        // It can be a part of Profile Creation in the future.
        String profileName = recordingPath.getFileName().toString().replace(".jfr", "");

        var profilingStartTime = new RecordingFileIterator<>(fullRecordingPath, new ProfilingStartTimeProcessor())
                .collect();

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, profileName, recordingPath.toString(), Instant.now(), profilingStartTime);

        Path profileInfoPath = workingDirs.createProfileInfo(profileInfo);
        LOG.info("New profile's info generated: profile_info={}", profileInfoPath);

        FlywayMigration.migrate(workingDirs, profileInfo);
        LOG.info("Schema migrated to the new database file: {}", workingDirs.profileDbFile(profileInfo));

        ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

        // Execute Post-create operation: pre-generate data and structures
        if (postCreateActions) {
            postCreateAction.execute(profileManager);
        }

        return profileManager;
    }

    @Override
    public Optional<ProfileManager> getProfile(String profileId) {
        return Optional.ofNullable(workingDirs.retrieveProfileInfo(profileId))
                .map(profileManagerFactory);
    }

    @Override
    public void deleteProfile(String profileId) {
        getProfile(profileId)
                .ifPresent(ProfileManager::cleanup);
    }
}
