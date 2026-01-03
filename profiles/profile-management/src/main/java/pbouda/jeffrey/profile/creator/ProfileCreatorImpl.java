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

package pbouda.jeffrey.profile.creator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfileCreationRepository;
import pbouda.jeffrey.provider.platform.repository.ProfileCreationRepository.InsertProfile;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.ProfileDatabaseProvider;
import pbouda.jeffrey.provider.profile.RecordingEventParser;
import pbouda.jeffrey.provider.profile.model.parser.ParserResult;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.profile.repository.ProfileRepositories;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class ProfileCreatorImpl implements ProfileCreator {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileCreatorImpl.class);

    private final ProjectInfo projectInfo;
    private final ProfileDatabaseProvider profileDatabaseProvider;
    private final ProjectRecordingStorage projectRecordingStorage;
    private final RecordingEventParser recordingEventParser;
    private final EventWriter.Factory eventWriterFactory;
    private final ProjectRecordingRepository recordingRepository;
    private final ProfileCreationRepository profileCreationRepository;
    private final ProfileRepositories profileRepositories;
    private final Clock clock;

    public ProfileCreatorImpl(
            ProjectInfo projectInfo,
            ProfileDatabaseProvider profileDatabaseProvider,
            ProfileRepositories profileRepositories,
            PlatformRepositories platformRepositories,
            ProjectRecordingStorage projectRecordingStorage,
            RecordingEventParser recordingEventParser,
            EventWriter.Factory eventWriterFactory,
            Clock clock) {

        this.projectInfo = projectInfo;
        this.profileDatabaseProvider = profileDatabaseProvider;
        this.profileRepositories = profileRepositories;
        this.recordingRepository = platformRepositories.newProjectRecordingRepository(projectInfo.id());
        this.projectRecordingStorage = projectRecordingStorage;
        this.recordingEventParser = recordingEventParser;
        this.eventWriterFactory = eventWriterFactory;
        this.clock = clock;
        this.profileCreationRepository = platformRepositories.newProfileCreationRepository();
    }

    @Override
    public String createProfile(String recordingId) {
        Recording recording = this.recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        // Resolve the path of the recording from the configured recordings directory
        Optional<Path> recordingPathOpt = this.projectRecordingStorage.findRecording(recordingId);
        if (recordingPathOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recording not found: recording_id=" + recordingId + " project_id=" + projectInfo.id());
        }

        // Profile name is by default the recording name
        String profileName = recording.recordingName();

        String profileId = IDGenerator.generate();
        Instant profileCreatedAt = clock.instant();

        var insertProfile = new InsertProfile(
                projectInfo.id(),
                profileId,
                profileName,
                recording.eventSource(),
                profileCreatedAt,
                recordingId,
                recording.recordingStartedAt(),
                recording.recordingFinishedAt());

        profileCreationRepository.insertProfile(insertProfile);

        // Create the profile directory and database
        DataSource dataSource = profileDatabaseProvider.create(profileId);
        try {
            // Write events to the profile's own database
            EventWriter eventWriter = eventWriterFactory.create(dataSource);
            ParserResult parserResult = recordingEventParser.start(eventWriter, recordingPathOpt.get());
            eventWriter.onComplete();

            // Store specific data in the profile cache
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(dataSource);
            parserResult.specificData()
                    .forEach(data -> cacheRepository.put(data.key(), data.content()));
        } finally {
            // Close the datasource to release the database file
            DataSourceUtils.close(dataSource);
        }

        // Finish the initialization of the profile in the platform database
        this.profileCreationRepository.initializeProfile(profileId);

        // Update Recording Finished At (information from Recordings does not have to be accurate)
        // Use the latest event timestamp as the recording finished at
        profileCreationRepository.updateFinishedAtTimestamp(profileId);

        long millis = clock.instant().minusMillis(profileCreatedAt.toEpochMilli()).toEpochMilli();
        LOG.info("Events persisted to the database: profile_id={} elapsed_ms={}", profileId, millis);

        return profileId;
    }
}
