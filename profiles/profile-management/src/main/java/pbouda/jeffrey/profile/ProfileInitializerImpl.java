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
import pbouda.jeffrey.profile.manager.AdditionalFilesManager;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfileRepository;
import pbouda.jeffrey.provider.platform.repository.ProfileRepository.InsertProfile;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.RecordingEventParser;
import pbouda.jeffrey.provider.profile.model.parser.ParserResult;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.profile.repository.ProfileRepositories;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class ProfileInitializerImpl implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerImpl.class);

    private final ProjectInfo projectInfo;
    private final PlatformRepositories platformRepositories;
    private final ProfileRepositories profileRepositories;
    private final DatabaseManager databaseManager;
    private final ProjectRecordingStorage projectRecordingStorage;
    private final RecordingEventParser recordingEventParser;
    private final EventWriter.Factory eventWriterFactory;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileDataInitializer profileDataInitializer;
    private final ProjectRecordingRepository recordingRepository;
    private final Clock clock;

    public ProfileInitializerImpl(
            ProjectInfo projectInfo,
            PlatformRepositories platformRepositories,
            ProfileRepositories profileRepositories,
            DatabaseManager databaseManager,
            ProjectRecordingStorage projectRecordingStorage,
            RecordingEventParser recordingEventParser,
            EventWriter.Factory eventWriterFactory,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            Clock clock) {

        this.projectInfo = projectInfo;
        this.platformRepositories = platformRepositories;
        this.profileRepositories = profileRepositories;
        this.databaseManager = databaseManager;
        this.projectRecordingStorage = projectRecordingStorage;
        this.recordingEventParser = recordingEventParser;
        this.eventWriterFactory = eventWriterFactory;
        this.profileManagerFactory = profileManagerFactory;
        this.profileDataInitializer = profileDataInitializer;
        this.recordingRepository = platformRepositories.newProjectRecordingRepository(projectInfo.id());
        this.clock = clock;
    }

    @Override
    public ProfileManager initialize(String recordingId) {
        Instant startedAt = clock.instant();

        // --- Create profile from recording ---
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        Optional<Path> recordingPathOpt = projectRecordingStorage.findRecording(recordingId);
        if (recordingPathOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recording not found: recording_id=" + recordingId + " project_id=" + projectInfo.id());
        }

        String profileId = IDGenerator.generate();
        Instant profileCreatedAt = clock.instant();

        ProfileRepository profileRepository = platformRepositories.newProfileRepository(profileId);

        var insertProfile = new InsertProfile(
                projectInfo.id(),
                recording.recordingName(),
                recording.eventSource(),
                profileCreatedAt,
                recordingId,
                recording.recordingStartedAt(),
                recording.recordingFinishedAt());

        profileRepository.insert(insertProfile);

        DataSource dataSource = databaseManager.open(profileId);
        try {
            EventWriter eventWriter = eventWriterFactory.create(dataSource);
            ParserResult parserResult = recordingEventParser.start(eventWriter, recordingPathOpt.get());
            eventWriter.onComplete();

            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(dataSource);
            parserResult.specificData()
                    .forEach(data -> cacheRepository.put(data.key(), data.content()));

            profileRepository.initializeProfile();

            // --- Initialize profile manager and data ---
            ProfileInfo profileInfo = platformRepositories.newProfileRepository(profileId).find()
                    .orElseThrow(() -> new RuntimeException("Could not find newly created profile: " + profileId));

            ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

            profileDataInitializer.initialize(profileManager);

            AdditionalFilesManager additionalFilesManager = profileManager.additionalFilesManager();
            additionalFilesManager.processAdditionalFiles(recordingId);

            platformRepositories.newProfileRepository(profileInfo.id())
                    .enableProfile();

            // Ensure all data is flushed to disk - especially important for WAL mode databases
            profileRepositories.databaseClientProvider(dataSource)
                    .provide(GroupLabel.INFRASTRUCTURE)
                    .walCheckpoint();

            long elapsedMs = clock.instant().toEpochMilli() - startedAt.toEpochMilli();
            LOG.info("Profile initialized and enabled: profile_id={} profile_name={} elapsed_ms={}",
                    profileInfo.id(), profileInfo.name(), elapsedMs);

            return profileManager;
        } finally {
            DataSourceUtils.close(dataSource);
        }
    }
}
