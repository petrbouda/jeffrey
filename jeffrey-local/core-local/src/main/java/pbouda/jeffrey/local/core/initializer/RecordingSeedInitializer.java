/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.local.core.initializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.shared.common.model.Recording;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class RecordingSeedInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingSeedInitializer.class);

    private final QuickAnalysisManager quickAnalysisManager;
    private final Path seedPath;

    public RecordingSeedInitializer(QuickAnalysisManager quickAnalysisManager, Path seedPath) {
        this.quickAnalysisManager = quickAnalysisManager;
        this.seedPath = seedPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOG.info("Seeding of recordings started: {}", seedPath);

        if (!Files.isDirectory(seedPath)) {
            LOG.warn("Seed recordings directory does not exist, skipping: path={}", seedPath);
            return;
        }

        Set<String> existingFilenames = quickAnalysisManager.listRecordings().stream()
                .map(Recording::recordingName)
                .collect(Collectors.toSet());

        int imported = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(seedPath, RecordingSeedInitializer::isRecordingFile)) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                if (existingFilenames.contains(filename)) {
                    LOG.debug("Seed recording already imported, skipping: filename={}", filename);
                    continue;
                }

                try (InputStream is = Files.newInputStream(file)) {
                    quickAnalysisManager.uploadRecording(filename, is, null);
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
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jfr") || name.endsWith(".hprof") || name.endsWith(".hprof.gz");
    }
}
