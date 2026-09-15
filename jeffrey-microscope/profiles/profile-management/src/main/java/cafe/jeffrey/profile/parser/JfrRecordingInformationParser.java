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

package cafe.jeffrey.profile.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.jfrparser.raw.JfrChunk;
import cafe.jeffrey.jfrparser.raw.JfrParser;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JfrRecordingInformationParser implements RecordingInformationParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingInformationParser.class);

    private final TempDirFactory tempDirFactory;

    public JfrRecordingInformationParser(TempDirFactory tempDirFactory) {
        this.tempDirFactory = tempDirFactory;
    }

    /**
     * Pools the chunks of every file and describes them once, so a recording spread over several
     * files reports the window it actually covers rather than the window of whichever file was
     * looked at. Reading a chunk list is a walk over headers, so the cost is per file and small.
     */
    @Override
    public RecordingInformation provide(RecordingSources sources) {
        List<JfrChunk> chunks = new ArrayList<>();
        for (Path recordingPath : sources.files()) {
            chunks.addAll(chunksOf(recordingPath));
        }
        return JfrParser.recordingInfo(chunks);
    }

    private List<JfrChunk> chunksOf(Path recordingPath) {
        if (Lz4Compressor.isLz4Compressed(recordingPath)) {
            // Try streaming first - works for single-frame LZ4 files
            try (InputStream lz4Stream = Lz4Compressor.decompressStream(recordingPath)) {
                return JfrParser.chunks(lz4Stream);
            } catch (Exception e) {
                // Fallback: decompress to temp file and parse
                // This handles multi-frame LZ4 files where streaming may fail at frame boundaries
                LOG.debug("Streaming LZ4 parsing failed, falling back to temp file decompression: {}",
                        recordingPath, e);
                return parseViaTemporaryFile(recordingPath);
            }
        } else {
            // Uncompressed .jfr file
            return JfrParser.chunks(recordingPath);
        }
    }

    private List<JfrChunk> parseViaTemporaryFile(Path recordingPath) {
        Lz4Compressor lz4Compressor = new Lz4Compressor(tempDirFactory);
        try (TempDirectory tempDir = tempDirFactory.newTempDir()) {
            Path decompressed = lz4Compressor.decompressToDir(recordingPath, tempDir.path());
            return JfrParser.chunks(decompressed);
        } catch (Exception e) {
            throw Exceptions.internal("Cannot read LZ4 recording info: " + recordingPath, e);
        }
    }
}
