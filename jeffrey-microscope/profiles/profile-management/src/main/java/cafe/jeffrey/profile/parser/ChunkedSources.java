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

package cafe.jeffrey.profile.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Splits each source file into its JFR chunks and parses those.
 * <p>
 * A JFR file is a run of self-contained chunks, so splitting one yields several independent parse
 * units out of a recording that arrived as a single file. The split costs a full read and a full
 * write of the recording, which is worth paying only while there are fewer files than there are
 * workers to read them — {@link SourceParseMode#of} is where that is decided.
 *
 * @param lz4Compressor used only by the eager fallback, when streaming the compressed source
 *                      straight into chunk files fails partway
 */
record ChunkedSources(Lz4Compressor lz4Compressor) implements SourceParseMode {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkedSources.class);

    private static final String CHUNKS_DIR = "chunks";
    private static final String CHUNKS_FALLBACK_DIR = "chunks-fallback";
    private static final String DECOMPRESSED_DIR = "decompressed";

    @Override
    public void expand(Path source, Path scratchDir, Consumer<Path> onUnit) {
        if (Lz4Compressor.isLz4Compressed(source)) {
            // Handed over only once the whole split has succeeded. A compressed source may fail
            // partway through and be retried from scratch, and chunks already handed to the
            // writers by the failed attempt would then be ingested twice.
            expandCompressed(source, scratchDir).forEach(onUnit);
        } else {
            // Handed over as each chunk is closed. Splitting reads the whole recording and writes
            // the same bytes back out, and nothing about the last chunk is needed to start reading
            // the first, so parsing on the callback puts the copy and the parse on top of each
            // other: the split costs roughly its first chunk rather than all of them.
            JfrParser.disassemble(source, scratchDir.resolve(CHUNKS_DIR), onUnit::accept);
        }
    }

    /**
     * Streams the compressed source straight into chunk files — a single pass, with no whole
     * decompressed copy on disk. If that fails, falls back to decompressing it in full first,
     * mirroring {@link JfrRecordingInformationParser}.
     */
    private List<Path> expandCompressed(Path source, Path scratchDir) {
        try {
            return JfrParser.disassemble(source, scratchDir.resolve(CHUNKS_DIR));
        } catch (Exception e) {
            // Defensive fallback: decompress the whole recording to disk first and split the plain
            // file. A fresh output directory is used so partially written chunk files from the
            // failed streaming attempt cannot leak into the result.
            LOG.warn("Streaming LZ4 disassembly failed, falling back to eager decompression: recording={}",
                    source, e);

            // Recovered, but not for free: the fallback writes the whole decompressed recording to
            // disk before reading any of it, so a parse that took twice as long and needed the space
            // has an explanation here rather than looking like an unexplained outlier.
            Notifications.of(NotificationType.RECORDING_DECOMPRESSION_FALLBACK)
                    .attribute("recording", String.valueOf(source))
                    .errorType(e)
                    .emit();

            Path decompressedDir = scratchDir.resolve(DECOMPRESSED_DIR);
            Path decompressed = lz4Compressor.decompressToDir(source, decompressedDir);
            return JfrParser.disassemble(decompressed, scratchDir.resolve(CHUNKS_FALLBACK_DIR));
        }
    }
}
