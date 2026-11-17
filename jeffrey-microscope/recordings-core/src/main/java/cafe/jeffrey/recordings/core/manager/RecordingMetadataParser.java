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

package cafe.jeffrey.recordings.core.manager;


import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reads a freshly ingested recording's profiling start and end from its files. The implementation
 * needs the profile parsing stack, so it lives in core-microscope and {@code recordings-core} stays
 * free of it; the recording's format is always read off its filename.
 */
@FunctionalInterface
public interface RecordingMetadataParser {

    /**
     * Reads one recording's metadata. A recording downloaded from the connector is several files, and the
     * answer is one recording's worth across all of them — the earliest start and the latest end —
     * because that window anchors the relative timeline every event is written against.
     */
    Optional<RecordingMetadata> parse(List<Path> recordingFiles);

    record RecordingMetadata(
            Instant recordingStartedAt,
            Instant recordingFinishedAt) {
    }
}
