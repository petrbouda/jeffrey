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

import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;
import pbouda.jeffrey.provider.reader.jfr.chunk.JfrParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class JfrRecordingInformationParser implements RecordingInformationParser {

    @Override
    public RecordingInformation provide(Path recordingPath) {
        if (Lz4Compressor.isLz4Compressed(recordingPath)) {
            // Stream directly from LZ4 compressed file - no temp file needed
            try (InputStream lz4Stream = Lz4Compressor.decompressStream(recordingPath)) {
                return JfrParser.recordingInfo(lz4Stream);
            } catch (IOException e) {
                throw new RuntimeException("Cannot read LZ4 recording info: " + recordingPath, e);
            }
        } else {
            // Uncompressed .jfr file
            return JfrParser.recordingInfo(recordingPath);
        }
    }
}
