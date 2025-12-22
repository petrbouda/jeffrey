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

package pbouda.jeffrey.provider.reader.jfr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;
import pbouda.jeffrey.provider.reader.jfr.chunk.Recordings;

import java.nio.file.Path;

public class JfrRecordingInformationParser implements RecordingInformationParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingInformationParser.class);
    private final JeffreyDirs jeffreyDirs;
    private final Lz4Compressor lz4Compressor;

    public JfrRecordingInformationParser(JeffreyDirs jeffreyDirs, Lz4Compressor lz4Compressor) {
        this.jeffreyDirs = jeffreyDirs;
        this.lz4Compressor = lz4Compressor;
    }

    @Override
    public RecordingInformation provide(Path recordingPath) {
        if (Lz4Compressor.isLz4Compressed(recordingPath)) {
            // Compressed .jfr.lz4 file, need to decompress first to a temp dir to parse info
            try (Directory tempDir = jeffreyDirs.newTempDir()) {
                Path decompressed = lz4Compressor.decompressToDir(recordingPath, tempDir.path());
                return Recordings.aggregatedRecordingInfo(decompressed);
            }
        } else {
            // Uncompressed .jfr file
            return Recordings.aggregatedRecordingInfo(recordingPath);
        }
    }
}
