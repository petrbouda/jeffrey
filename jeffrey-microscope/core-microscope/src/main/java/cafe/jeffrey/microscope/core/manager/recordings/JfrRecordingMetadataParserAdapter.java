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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Adapts microscope's profile-stack {@link RecordingInformationParser} to the
 * {@code recordings-core} {@link RecordingMetadataParser} SPI, swallowing parse failures
 * (so ingestion falls back to filename-based event-source detection, matching prior behavior).
 */
public class JfrRecordingMetadataParserAdapter implements RecordingMetadataParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingMetadataParserAdapter.class);

    private final RecordingInformationParser delegate;

    public JfrRecordingMetadataParserAdapter(RecordingInformationParser delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<RecordingMetadata> parse(List<Path> recordingFiles) {
        try {
            RecordingInformation info = delegate.provide(new RecordingSources(recordingFiles));
            return Optional.of(new RecordingMetadata(
                    info.eventSource(),
                    info.recordingStartedAt(),
                    info.recordingFinishedAt()));
        } catch (Exception e) {
            LOG.warn("Failed to parse recording metadata: files={} error={}", recordingFiles, e.getMessage());
            return Optional.empty();
        }
    }
}
