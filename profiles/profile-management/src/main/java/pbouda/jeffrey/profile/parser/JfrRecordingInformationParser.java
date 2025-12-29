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

package pbouda.jeffrey.profile.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.compression.Lz4Compressor;
import pbouda.jeffrey.shared.filesystem.JeffreyDirs;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;
import pbouda.jeffrey.profile.parser.chunk.JfrParser;

import java.io.InputStream;
import java.nio.file.Path;

public class JfrRecordingInformationParser implements RecordingInformationParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingInformationParser.class);

    private final JeffreyDirs jeffreyDirs;

    public JfrRecordingInformationParser(JeffreyDirs jeffreyDirs) {
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public RecordingInformation provide(Path recordingPath) {
        if (Lz4Compressor.isLz4Compressed(recordingPath)) {
            // Try streaming first - works for single-frame LZ4 files
            try (InputStream lz4Stream = Lz4Compressor.decompressStream(recordingPath)) {
                return JfrParser.recordingInfo(lz4Stream);
            } catch (Exception e) {
                // Fallback: decompress to temp file and parse
                // This handles multi-frame LZ4 files where streaming may fail at frame boundaries
                LOG.debug("Streaming LZ4 parsing failed, falling back to temp file decompression: {}",
                        recordingPath, e);
                return parseViaTemporaryFile(recordingPath);
            }
        } else {
            // Uncompressed .jfr file
            return JfrParser.recordingInfo(recordingPath);
        }
    }

    private RecordingInformation parseViaTemporaryFile(Path recordingPath) {
        Lz4Compressor lz4Compressor = new Lz4Compressor(jeffreyDirs);
        try (JeffreyDirs.Directory tempDir = jeffreyDirs.newTempDir()) {
            Path decompressed = lz4Compressor.decompressToDir(recordingPath, tempDir.path());
            return JfrParser.recordingInfo(decompressed);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read LZ4 recording info: " + recordingPath, e);
        }
    }
}
