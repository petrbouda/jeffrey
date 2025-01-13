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
import pbouda.jeffrey.FlywayMigration;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.generator.basic.StartEndTimeCollector;
import pbouda.jeffrey.generator.basic.StartEndTimeEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.processor.DatabaseEventPushProcessor;
import pbouda.jeffrey.repository.profile.ProfileEventRepository;
import pbouda.jeffrey.repository.profile.ProfileRepositories;
import pbouda.jeffrey.repository.profile.ProfileRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class ProfileInitializerManagerImpl implements ProfileInitializationManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerManagerImpl.class);

    private final ProjectDirs projectDirs;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileRecordingInitializer.Factory profileRecordingInitializerFactory;
    private final ProfileRepositories profileRepositories;

    public ProfileInitializerManagerImpl(
            ProjectDirs projectDirs,
            ProfileManager.Factory profileManagerFactory,
            ProfileRecordingInitializer.Factory profileRecordingInitializerFactory,
            ProfileRepositories profileRepositories) {

        this.projectDirs = projectDirs;
        this.profileManagerFactory = profileManagerFactory;
        this.profileRecordingInitializerFactory = profileRecordingInitializerFactory;
        this.profileRepositories = profileRepositories;
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
        recordingInitializer.initialize(profileId, absoluteOriginalRecordingPath);

        // Name derived from the relativeRecordingPath
        // It can be a part of Profile Creation in the future.
        String profileName = relativeRecordingPath.getFileName().toString().replace(".jfr", "");

        var startEndTime = JdkRecordingIterators.automaticAndCollectPartial(
                profileDirs.allRecordingPaths(),
                StartEndTimeEventProcessor::new,
                new StartEndTimeCollector());

        if (startEndTime.start() == null || startEndTime.end() == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve the start and end time of the recording: path=" + relativeRecordingPath +
                            " start_end_time=" + startEndTime);
        }

        ProfileInfo profileInfo = new ProfileInfo(
                profileId,
                projectId,
                profileName,
                relativeRecordingPath.toString(),
                Instant.now(),
                startEndTime.start(),
                startEndTime.end());

        Path profileInfoPath = profileDirs.saveInfo(profileInfo);
        LOG.info("New profile's info generated: profile_info={}", profileInfoPath);

        FlywayMigration.migrate(profileDirs);
        LOG.info("Schema migrated to the new database file: {}", profileDirs.database());

        ProfileEventRepository eventRepository = profileRepositories.events(profileDirs);

        long start = System.nanoTime();
        JdkRecordingIterators.automatic(
                        profileDirs.allRecordingPaths(),
                        () -> new DatabaseEventPushProcessor(eventRepository))
                .justIterate();
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: elapsed_ms={}", millis);

        return profileManagerFactory.apply(profileInfo);
    }
}
