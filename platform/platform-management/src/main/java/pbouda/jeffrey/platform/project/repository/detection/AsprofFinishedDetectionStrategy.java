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

package pbouda.jeffrey.platform.project.repository.detection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record AsprofFinishedDetectionStrategy(Duration finishedPeriod, Clock clock) implements FinishedDetectionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFinishedDetectionStrategy.class);

    @Override
    public RecordingStatus determineStatus(Path sessionPath) {
        // Check if path exists and is a directory
        if (!FileSystemUtils.isDirectory(sessionPath)) {
            return RecordingStatus.FINISHED;
        }

        try {
            // Check if any ASPROF files exist in the session directory
            boolean hasAsprofFiles = hasAsprofFiles(sessionPath);

            if (!hasAsprofFiles) {
                // No ASPROF files found → FINISHED
                return RecordingStatus.FINISHED;
            }

            // ASPROF files exist, check if they're recent enough to be considered active
            Optional<Instant> latestAsprofModification = getLatestAsprofModification(sessionPath);

            if (latestAsprofModification.isEmpty()) {
                // Shouldn't happen since we found ASPROF files, but handle gracefully
                return RecordingStatus.FINISHED;
            }

            Instant now = clock.instant();
            Instant fileTimeWithPeriod = latestAsprofModification.get().plus(finishedPeriod);

            if (now.isAfter(fileTimeWithPeriod)) {
                // ASPROF files exist but are after finished period → FINISHED
                return RecordingStatus.FINISHED;
            } else {
                // ASPROF files exist and are within finished period → ACTIVE
                return RecordingStatus.ACTIVE;
            }

        } catch (IOException e) {
            LOG.warn("Failed to check ASPROF files in session path: {}. Defaulting to FINISHED status.", sessionPath, e);
            return RecordingStatus.FINISHED;
        }
    }

    private static boolean hasAsprofFiles(Path sessionPath) throws IOException {
        return !asprofFiles(sessionPath).isEmpty();
    }

    private static Optional<Instant> getLatestAsprofModification(Path sessionPath) throws IOException {
        return asprofFiles(sessionPath).stream()
                .map(FileSystemUtils::modifiedAt)
                .max(Instant::compareTo);
    }

    private static List<Path> asprofFiles(Path sessionPath) throws IOException {
        try (Stream<Path> files = Files.list(sessionPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(FileSystemUtils::isNotHidden)
                    .filter(SupportedRecordingFile.ASPROF_TEMP::matches)
                    .toList();
        }
    }
}
