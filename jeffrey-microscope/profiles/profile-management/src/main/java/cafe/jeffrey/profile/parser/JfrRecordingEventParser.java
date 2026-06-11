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
import cafe.jeffrey.jfrparser.jdk.EventProcessor;
import cafe.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import cafe.jeffrey.profile.parser.chunk.JfrParser;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class JfrRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingEventParser.class);

    private static final String CHUNKS_DIR = "chunks";
    private static final String CHUNKS_FALLBACK_DIR = "chunks-fallback";

    private final TempDirFactory tempDirFactory;
    private final Lz4Compressor lz4Compressor;

    public JfrRecordingEventParser(TempDirFactory tempDirFactory, Lz4Compressor lz4Compressor) {
        this.tempDirFactory = tempDirFactory;
        this.lz4Compressor = lz4Compressor;
    }

    @Override
    public void start(EventWriter eventWriter, Path recording) {
        try (TempDirectory tempDir = tempDirFactory.newTempDir()) {
            LOG.info("Created the profile's temporary folder: {}", tempDir.path());

            List<Path> recordingChunks = disassembleToChunks(recording, tempDir);
            Supplier<EventProcessor<Void>> eventProcessor =
                    () -> new JfrEventReader(eventWriter.newSingleThreadedWriter());

            JdkRecordingIterators.parallelAndWait(recordingChunks, eventProcessor);
        }
    }

    /**
     * Disassembles the recording into chunk files. LZ4 compressed recordings are streamed
     * directly into the chunk files (single pass over the compressed data, no intermediate
     * decompressed copy on disk). If streaming fails, it falls back to the eager
     * decompress-to-dir path, mirroring {@link JfrRecordingInformationParser}.
     */
    private List<Path> disassembleToChunks(Path recording, TempDirectory tempDir) {
        if (!Lz4Compressor.isLz4Compressed(recording)) {
            return JfrParser.disassemble(recording, tempDir.path().resolve(CHUNKS_DIR));
        }

        try {
            return JfrParser.disassemble(recording, tempDir.path().resolve(CHUNKS_DIR));
        } catch (Exception e) {
            // Defensive fallback: decompress the whole recording to disk first and disassemble
            // the plain file. A fresh output directory is used so partially written chunk files
            // from the failed streaming attempt cannot leak into the result.
            LOG.warn("Streaming LZ4 disassembly failed, falling back to eager decompression: recording={}",
                    recording, e);
            Path decompressed = lz4Compressor.decompressToDir(recording, tempDir.path());
            return JfrParser.disassemble(decompressed, tempDir.path().resolve(CHUNKS_FALLBACK_DIR));
        }
    }
}
