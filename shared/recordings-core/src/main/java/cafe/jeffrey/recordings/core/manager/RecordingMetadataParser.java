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

package cafe.jeffrey.recordings.core.manager;

import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Optional SPI that enriches a freshly ingested recording with metadata parsed from the file
 * (event source, profiling start/end). Implementations that depend on the profile parsing stack
 * live in the deployment module; the {@link #NOOP} default keeps {@code recordings-core} free of
 * any profile coupling, falling back to filename-based event-source detection.
 */
@FunctionalInterface
public interface RecordingMetadataParser {

    Optional<RecordingMetadata> parse(Path recordingFile);

    record RecordingMetadata(
            RecordingEventSource eventSource,
            Instant recordingStartedAt,
            Instant recordingFinishedAt) {
    }

    RecordingMetadataParser NOOP = file -> Optional.empty();
}
