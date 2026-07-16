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

import cafe.jeffrey.pprofparser.PprofRecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;

/**
 * Selects the {@link RecordingInformationParser} for an uploaded recording by its file type: pprof
 * files ({@code .pprof} / {@code .pb.gz}) go to the pprof parser, everything else (JFR and its LZ4
 * variant) to the JFR parser. Upload-time metadata (event source + time window) is otherwise read
 * uniformly regardless of the recording format.
 */
public class FileTypeDispatchingRecordingInformationParser implements RecordingInformationParser {

    private final RecordingInformationParser jfrParser;
    private final RecordingInformationParser pprofParser;

    public FileTypeDispatchingRecordingInformationParser(
            RecordingInformationParser jfrParser,
            RecordingInformationParser pprofParser) {

        this.jfrParser = jfrParser;
        this.pprofParser = pprofParser;
    }

    public FileTypeDispatchingRecordingInformationParser(RecordingInformationParser jfrParser) {
        this(jfrParser, new PprofRecordingInformationParser());
    }

    @Override
    public RecordingInformation provide(Path recordingPath) {
        if (SupportedRecordingFile.of(recordingPath) == SupportedRecordingFile.PPROF) {
            return pprofParser.provide(recordingPath);
        }
        return jfrParser.provide(recordingPath);
    }
}
