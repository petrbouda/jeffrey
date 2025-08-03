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

package pbouda.jeffrey.project.repository.detection;

import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.repository.RecordingStatus;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record WithoutDetectionFileStrategy(Duration finishedPeriod, Clock clock) implements StatusStrategy {

        @Override
        public RecordingStatus determineStatus(Path sessionPath) {
            Optional<Instant> modifiedAtOpt = FileSystemUtils.directoryModification(sessionPath);
            if (modifiedAtOpt.isEmpty()) {
                // No Raw Recordings in the Recording Session folder
                return RecordingStatus.UNKNOWN;
            } else if (clock.instant().isAfter(modifiedAtOpt.get().plus(finishedPeriod))) {
                // Latest modification with finished-period passed
                return RecordingStatus.FINISHED;
            } else {
                // Finished-period has not passed, but we cannot say it's active because we don't know the detection file
                return RecordingStatus.UNKNOWN;
            }
        }
    }
