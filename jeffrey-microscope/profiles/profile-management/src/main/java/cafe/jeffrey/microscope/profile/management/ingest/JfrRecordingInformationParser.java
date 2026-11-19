/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.profile.management.ingest;

import cafe.jeffrey.microscope.common.compression.Lz4Decompressor;
import cafe.jeffrey.microscope.common.exception.Exceptions;
import cafe.jeffrey.microscope.common.filesystem.TempDirFactory;
import cafe.jeffrey.microscope.common.filesystem.TempDirectory;
import cafe.jeffrey.microscope.profile.parser.jfr.raw.JfrChunk;
import cafe.jeffrey.microscope.profile.parser.jfr.raw.JfrParser;
import cafe.jeffrey.microscope.profile.persistence.api.RecordingInformation;
import cafe.jeffrey.microscope.profile.persistence.api.RecordingInformationParser;
import cafe.jeffrey.microscope.profile.persistence.api.RecordingSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        if (Lz4Decompressor.isLz4Compressed(recordingPath)) {
            // Try streaming first - works for single-frame LZ4 files
            try (InputStream lz4Stream = Lz4Decompressor.decompressStream(recordingPath)) {
                return JfrParser.collectChunks(lz4Stream);
            } catch (Exception e) {
                // Fallback: decompress to temp file and parse
                // This handles multi-frame LZ4 files where streaming may fail at frame boundaries
                LOG.debug("Streaming LZ4 parsing failed, falling back to temp file decompression: {}",
                        recordingPath, e);
                return parseViaTemporaryFile(recordingPath);
            }
        } else {
            // Uncompressed .jfr file
            return JfrParser.collectChunks(recordingPath);
        }
    }

    private List<JfrChunk> parseViaTemporaryFile(Path recordingPath) {
        Lz4Decompressor lz4Decompressor = new Lz4Decompressor(tempDirFactory);
        try (TempDirectory tempDir = tempDirFactory.newTempDir()) {
            Path decompressed = lz4Decompressor.decompressToDir(recordingPath, tempDir.path());
            return JfrParser.collectChunks(decompressed);
        } catch (Exception e) {
            throw Exceptions.internal("Cannot read LZ4 recording info: " + recordingPath, e);
        }
    }
}
