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

package pbouda.jeffrey.provider.writer.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.model.IngestionContext;
import pbouda.jeffrey.provider.api.model.parser.ParserResult;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.writer.sql.internal.InternalProfileRepository;
import pbouda.jeffrey.provider.writer.sql.internal.InternalRecordingRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileCacheRepository;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

public class SQLProfileInitializer implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SQLProfileInitializer.class);

    private final ProjectInfo projectInfo;
    private final ProjectRecordingStorage projectRecordingStorage;
    private final RecordingEventParser recordingEventParser;
    private final Function<String, EventWriter> eventWriterFactory;
    private final EventFieldsSetting eventFieldsSetting;
    private final InternalProfileRepository profileRepository;
    private final InternalRecordingRepository recordingRepository;
    private final Function<String, ProfileCacheRepository> cacheRepositoryFn;
    private final Clock clock;

    public SQLProfileInitializer(
            ProjectInfo projectInfo,
            DataSource dataSource,
            ProjectRecordingStorage projectRecordingStorage,
            RecordingEventParser recordingEventParser,
            Function<String, EventWriter> eventWriterFactory,
            EventFieldsSetting eventFieldsSetting,
            Clock clock) {

        this.projectInfo = projectInfo;
        this.projectRecordingStorage = projectRecordingStorage;
        this.recordingEventParser = recordingEventParser;
        this.eventWriterFactory = eventWriterFactory;
        this.eventFieldsSetting = eventFieldsSetting;
        this.clock = clock;
        this.recordingRepository = new InternalRecordingRepository(dataSource);
        this.profileRepository = new InternalProfileRepository(dataSource, clock);
        this.cacheRepositoryFn = profileId -> new JdbcProfileCacheRepository(profileId, dataSource);
    }

    @Override
    public String newProfile(String recordingId) {
        Recording recording = this.recordingRepository.findById(projectInfo.id(), recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        // Resolve the path of the recording from the configured recordings directory
        Optional<Path> recordingPathOpt = this.projectRecordingStorage.findRecording(recordingId);
        if (recordingPathOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recording not found: recording_id=" + recordingId + " project_id=" + projectInfo.id());
        }

        // Profile name is by default the recording name
        String profileName = recording.recordingName();

        IngestionContext ingestionContext = new IngestionContext(recording.recordingStartedAt(), eventFieldsSetting);

        String profileId = IDGenerator.generate();
        Instant profileCreatedAt = clock.instant();

        var insertProfile = new InternalProfileRepository.InsertProfile(
                projectInfo.id(),
                profileId,
                profileName,
                recording.eventSource(),
                eventFieldsSetting,
                profileCreatedAt,
                recordingId,
                recording.recordingStartedAt(),
                recording.recordingFinishedAt());

        profileRepository.insertProfile(insertProfile);

        EventWriter eventWriter = eventWriterFactory.apply(profileId);
        ParserResult parserResult = recordingEventParser.start(
                eventWriter, ingestionContext, recordingPathOpt.get());

        eventWriter.onComplete();

        // Update Recording Finished At (information from Recordings does not have to be accurate)
        // Use the latest event timestamp as the recording finished at
        profileRepository.updateFinishedAtTimestamp(profileId);

        ProfileCacheRepository cacheRepository = this.cacheRepositoryFn.apply(profileId);
        parserResult.specificData()
                .forEach(data -> cacheRepository.put(data.key(), data.content()));

        long millis = clock.instant().minusMillis(profileCreatedAt.toEpochMilli()).toEpochMilli();
        LOG.info("Events persisted to the database: profile_id={} elapsed_ms={}", profileId, millis);

        return profileId;
    }
}
