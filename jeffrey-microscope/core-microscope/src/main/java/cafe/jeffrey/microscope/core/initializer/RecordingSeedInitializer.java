/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.initializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class RecordingSeedInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingSeedInitializer.class);

    private static final Set<ManagedFile> SEED_FILE_TYPES = Set.of(
            ManagedFile.JFR,
            ManagedFile.JFR_LZ4,
            ManagedFile.HEAP_DUMP,
            ManagedFile.HEAP_DUMP_GZ
    );

    private final RecordingsManager recordingsManager;
    private final Path seedPath;

    public RecordingSeedInitializer(RecordingsManager recordingsManager, Path seedPath) {
        this.recordingsManager = recordingsManager;
        this.seedPath = seedPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOG.info("Seeding of recordings started: {}", seedPath);

        if (!Files.isDirectory(seedPath)) {
            LOG.warn("Seed recordings directory does not exist, skipping: path={}", seedPath);
            return;
        }

        List<String> existingFilenames = recordingsManager.listRecordings().stream()
                .map(Recording::recordingName)
                .toList();

        int imported = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(seedPath, RecordingSeedInitializer::isRecordingFile)) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                if (existingFilenames.contains(filename)) {
                    LOG.debug("Seed recording already imported, skipping: filename={}", filename);
                    continue;
                }

                try (InputStream is = Files.newInputStream(file)) {
                    recordingsManager.uploadRecording(filename, is, null);
                    imported++;
                    LOG.info("Seed recording imported: filename={}", filename);
                } catch (Exception e) {
                    LOG.warn("Failed to import seed recording: filename={} error={}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to scan seed recordings directory: path={} error={}", seedPath, e.getMessage());
        }

        if (imported > 0) {
            LOG.info("Seed recordings import completed: imported={} directory={}", imported, seedPath);
        }
    }

    private static boolean isRecordingFile(Path path) {
        return SEED_FILE_TYPES.contains(ManagedFile.of(path));
    }
}
