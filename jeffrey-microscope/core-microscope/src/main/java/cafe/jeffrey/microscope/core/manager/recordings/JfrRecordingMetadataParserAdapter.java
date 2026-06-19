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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser;

import java.nio.file.Path;
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
    public Optional<RecordingMetadata> parse(Path recordingFile) {
        try {
            RecordingInformation info = delegate.provide(recordingFile);
            return Optional.of(new RecordingMetadata(
                    info.eventSource(),
                    info.recordingStartedAt(),
                    info.recordingFinishedAt()));
        } catch (Exception e) {
            LOG.warn("Failed to parse recording metadata: file={} error={}", recordingFile, e.getMessage());
            return Optional.empty();
        }
    }
}
