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

package pbouda.jeffrey.provider.reader.jfr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.model.GenerateProfile;
import pbouda.jeffrey.provider.reader.jfr.recording.RecordingInitializer;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class JfrProfileInitializer implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(JfrProfileInitializer.class);

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSS");

    private final RecordingInitializer recordingInitializer;
    private final Path tempFolder;
    private final boolean keepSourceFiles;
    private final EventWriter writer;

    public JfrProfileInitializer(
            EventWriter writer,
            RecordingInitializer recordingInitializer,
            Path tempFolder,
            boolean keepSourceFiles) {

        this.writer = writer;
        this.recordingInitializer = recordingInitializer;
        this.tempFolder = tempFolder;
        this.keepSourceFiles = keepSourceFiles;
    }

    @Override
    public ProfileInfo newProfile(String projectId, Path originalRecordingPath) {
        // Name derived from the relativeRecordingPath
        // It can be a part of Profile Creation in the future.
        String profileName = originalRecordingPath.getFileName().toString().replace(".jfr", "");

        String folderName = Instant.now().atZone(ZoneOffset.UTC).format(DATETIME_FORMATTER);
        Path profileTempFolder = tempFolder.resolve(folderName);

        FileSystemUtils.createDirectories(profileTempFolder);
        LOG.info("Created the profile's temporary folder: {}", profileTempFolder);

        // Copies one or more recordings to the profile's directory
        List<Path> recordings = recordingInitializer.initialize(profileTempFolder, originalRecordingPath);

        var startEndTime = JdkRecordingIterators.automaticAndCollectPartial(
                recordings, StartEndTimeEventProcessor::new, new StartEndTimeCollector());

        if (startEndTime.start() == null || startEndTime.end() == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve the start and end time of the recording: path=" + recordings +
                            " start_end_time=" + startEndTime);
        }

        long start = System.nanoTime();

        String profileId = UUID.randomUUID().toString();
        GenerateProfile generateProfile = new GenerateProfile(
                profileId,
                projectId,
                profileName,
                startEndTime,
                recordings);

        writer.onStart(generateProfile);

        ProfileInfo profileInfo = JdkRecordingIterators.automaticAndCollect(
                recordings,
                () -> new JfrEventReaderProcessor(startEndTime, writer.newSingleThreadedWriter()),
                new WriterOnCompleteCollector(writer));
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        LOG.info("Events persisted to the database: elapsed_ms={}", millis);

        if (!keepSourceFiles) {
            FileSystemUtils.removeDirectory(profileTempFolder);
            LOG.info("Removed the profile's temporary folder: {}", profileTempFolder);
        }

        return profileInfo;
    }
}
