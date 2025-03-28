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
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.model.IngestionContext;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.reader.jfr.data.AutoAnalysisDataProvider;
import pbouda.jeffrey.provider.reader.jfr.data.JfrSpecificDataProvider;
import pbouda.jeffrey.provider.reader.jfr.recording.RecordingInitializer;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

public class JfrProfileInitializer implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(JfrProfileInitializer.class);

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSS");

    private final RecordingInitializer recordingInitializer;
    private final Path tempFolder;
    private final boolean keepSourceFiles;
    private final EventFieldsSetting eventFieldsSetting;
    private final EventWriter writer;

    private final List<JfrSpecificDataProvider> specificDataProviders =
            List.of(new AutoAnalysisDataProvider());

    public JfrProfileInitializer(
            EventWriter writer,
            RecordingInitializer recordingInitializer,
            Path tempFolder,
            boolean keepSourceFiles,
            EventFieldsSetting eventFieldsSetting) {

        this.writer = writer;
        this.recordingInitializer = recordingInitializer;
        this.tempFolder = tempFolder;
        this.keepSourceFiles = keepSourceFiles;
        this.eventFieldsSetting = eventFieldsSetting;
    }

    @Override
    public String newProfile(String projectId, Path originalRecordingPath) {
        String folderName = Instant.now().atZone(ZoneOffset.UTC).format(DATETIME_FORMATTER);
        Path profileTempFolder = tempFolder.resolve(folderName);

        FileSystemUtils.createDirectories(profileTempFolder);
        try {
            return _newProfile(projectId, originalRecordingPath, profileTempFolder);
        } finally {
            if (!keepSourceFiles) {
                FileSystemUtils.removeDirectory(profileTempFolder);
                LOG.info("Removed the profile's temporary folder: {}", profileTempFolder);
            }
        }
    }

    private String _newProfile(String projectId, Path originalRecordingPath, Path profileTempFolder) {
        // Name derived from the relativeRecordingPath
        // It can be a part of Profile Creation in the future.
        String profileName = originalRecordingPath.getFileName().toString().replace(".jfr", "");

        FileSystemUtils.createDirectories(profileTempFolder);
        LOG.info("Created the profile's temporary folder: {}", profileTempFolder);

        // Copies one or more recordings to the profile's directory
        List<Path> recordings = recordingInitializer.initialize(profileTempFolder, originalRecordingPath);

        var resolvedSourceInfo = JdkRecordingIterators.automaticAndCollectPartial(
                recordings, RecordingSourceInformationProcessor::new, new RecordingSourceInformationCollector());

        long start = System.nanoTime();

        IngestionContext ingestionContext = new IngestionContext(
                projectId,
                profileName,
                resolvedSourceInfo.profilingStart(),
                resolvedSourceInfo.eventSource(),
                eventFieldsSetting);

        writer.onStart(ingestionContext);

        Supplier<EventProcessor<Void>> eventProcessor = () -> {
            return new JfrEventReader(writer.newSingleThreadedWriter(), ingestionContext);
        };

        String newProfileId = JdkRecordingIterators.automaticAndCollect(
                recordings, eventProcessor, new WriterOnCompleteCollector(writer));

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Events persisted to the database: profile_id={} elapsed_ms={}", newProfileId, millis);

        start = System.nanoTime();
        ProfileCacheRepository cacheRepository = writer.newProfileCacheRepository();
        specificDataProviders.stream()
                .map(provider -> provider.provide(recordings))
                .forEach(item -> cacheRepository.put(item.key(), item.data()));
        millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("JFR-specific data generated and cached: profile_id={} elapsed_ms={}", newProfileId, millis);

        return newProfileId;
    }
}
