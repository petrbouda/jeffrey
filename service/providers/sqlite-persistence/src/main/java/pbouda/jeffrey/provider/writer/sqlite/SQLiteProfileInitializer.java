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

package pbouda.jeffrey.provider.writer.sqlite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.model.IngestionContext;
import pbouda.jeffrey.provider.api.model.parser.ParserResult;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.writer.sqlite.internal.InternalProfileRepository;
import pbouda.jeffrey.provider.writer.sqlite.internal.InternalRecordingRepository;
import pbouda.jeffrey.provider.writer.sqlite.repository.JdbcProfileCacheRepository;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SQLiteProfileInitializer implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SQLiteProfileInitializer.class);

    private final String projectId;
    private final Path recordingsDir;
    private final RecordingEventParser recordingEventParser;
    private final EventWriter eventWriter;
    private final EventFieldsSetting eventFieldsSetting;
    private final InternalProfileRepository profileRepository;
    private final InternalRecordingRepository recordingRepository;
    private final ProfileCacheRepository cacheRepository;

    public SQLiteProfileInitializer(
            String projectId,
            Path recordingsDir,
            DataSource dataSource,
            RecordingEventParser recordingEventParser,
            EventWriter eventWriter,
            EventFieldsSetting eventFieldsSetting) {

        this.projectId = projectId;
        this.recordingsDir = recordingsDir;
        this.recordingEventParser = recordingEventParser;
        this.eventWriter = eventWriter;
        this.eventFieldsSetting = eventFieldsSetting;
        this.recordingRepository = new InternalRecordingRepository(dataSource);
        this.profileRepository = new InternalProfileRepository(dataSource);
        this.cacheRepository = new JdbcProfileCacheRepository(projectId, new JdbcTemplate(dataSource));
    }

    @Override
    public String newProfile(String recordingId) {
        Recording recording = this.recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        // Resolve the path of the recording from the configured recordings directory
        Path recordingPath = recordingsDir.resolve(recording.recordingFilename());

        // Profile name is by default the recording name
        String profileName = recording.recordingName();

        long start = System.nanoTime();

        IngestionContext ingestionContext = new IngestionContext(recording.recordingStartedAt(), eventFieldsSetting);

        String profileId = IDGenerator.generate();
        Instant profileCreatedAt = Instant.now();

        var insertProfile = new InternalProfileRepository.InsertProfile(
                projectId,
                profileId,
                profileName,
                recording.eventSource(),
                eventFieldsSetting,
                profileCreatedAt,
                recordingId,
                recording.recordingStartedAt(),
                recording.recordingFinishedAt());

        profileRepository.insertProfile(insertProfile);

        ParserResult parserResult = recordingEventParser.start(eventWriter, ingestionContext, recordingPath);

        eventWriter.onComplete();

        parserResult.specificData()
                .forEach(data ->  cacheRepository.put(data.key(), data.content()));

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: profile_id={} elapsed_ms={}", profileId, millis);

        return profileId;
    }
}
