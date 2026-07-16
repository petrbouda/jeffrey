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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;

/**
 * Routes recording-metadata parsing by the recording's file type: OpenTelemetry profiles
 * ({@code .otlp}) are parsed by the OTLP parser, everything else falls through to the JFR parser
 * (matching the previous behavior for JFR and unrecognized files).
 */
public class FileTypeDispatchingRecordingInformationParser implements RecordingInformationParser {

    private final RecordingInformationParser jfrParser;
    private final RecordingInformationParser otlpParser;

    public FileTypeDispatchingRecordingInformationParser(
            RecordingInformationParser jfrParser,
            RecordingInformationParser otlpParser) {

        this.jfrParser = jfrParser;
        this.otlpParser = otlpParser;
    }

    @Override
    public RecordingInformation provide(Path recordingPath) {
        if (SupportedRecordingFile.of(recordingPath) == SupportedRecordingFile.OTLP_PROFILE) {
            return otlpParser.provide(recordingPath);
        }
        return jfrParser.provide(recordingPath);
    }
}
