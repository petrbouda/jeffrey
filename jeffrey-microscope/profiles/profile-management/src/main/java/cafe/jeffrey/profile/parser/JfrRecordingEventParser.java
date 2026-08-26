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
import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

            Supplier<EventProcessor<Void>> eventProcessor =
                    () -> new JfrEventReader(eventWriter.newSingleThreadedWriter());

            if (Lz4Compressor.isLz4Compressed(recording)) {
                JdkRecordingIterators.parallelAndWait(
                        disassembleCompressed(recording, tempDir), eventProcessor);
            } else {
                parseWhileDisassembling(recording, tempDir, eventProcessor);
            }
        }
    }

    /**
     * Splits the recording and parses each chunk as soon as it has been written.
     * <p>
     * Splitting reads the whole recording and writes the same bytes back out beside it, and none of
     * that overlapped with the parse it exists to feed: the first chunk sat finished on disk until
     * the last one had been copied. Parsing on the callback puts the copy and the parse on top of
     * each other, so the split costs roughly its first chunk rather than all of them.
     * <p>
     * Only for recordings that are not LZ4 compressed. A compressed one may fail partway through
     * and be retried from scratch (see {@link #disassembleCompressed}), and chunks already handed
     * to the writers by the failed attempt would then be ingested twice.
     */
    private void parseWhileDisassembling(
            Path recording, TempDirectory tempDir, Supplier<EventProcessor<Void>> eventProcessor) {

        List<CompletableFuture<Void>> parsing = new ArrayList<>();
        try {
            JfrParser.disassemble(recording, tempDir.path().resolve(CHUNKS_DIR),
                    chunk -> parsing.add(JdkRecordingIterators.parseAsync(chunk, eventProcessor.get())));
        } catch (RuntimeException e) {
            // The chunks already submitted are still writing into this profile's database. Let them
            // finish before the failure unwinds, so nothing is still appending to it while the
            // temp directory is deleted and the initialization is torn down.
            awaitQuietly(parsing);
            throw e;
        }

        CompletableFuture.allOf(parsing.toArray(CompletableFuture[]::new)).join();
    }

    private static void awaitQuietly(List<CompletableFuture<Void>> parsing) {
        try {
            CompletableFuture.allOf(parsing.toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException suppressed) {
            LOG.debug("A chunk parse also failed while unwinding a failed disassembly", suppressed);

            // At DEBUG this failure is invisible in practice, and it is the second of two: whatever
            // ends up reported to the user is the disassembly error, not this. Recording it keeps the
            // real first cause reachable when the reported one turns out to be a symptom.
            Notifications.of(NotificationType.RECORDING_CHUNK_FAILURE_SWALLOWED)
                    .errorType(suppressed)
                    .emit();
        }
    }

    /**
     * Disassembles an LZ4 compressed recording into chunk files. The compressed data is streamed
     * straight into the chunk files (a single pass, no intermediate decompressed copy on disk). If
     * streaming fails, it falls back to the eager decompress-to-dir path, mirroring
     * {@link JfrRecordingInformationParser}.
     */
    private List<Path> disassembleCompressed(Path recording, TempDirectory tempDir) {
        try {
            return JfrParser.disassemble(recording, tempDir.path().resolve(CHUNKS_DIR));
        } catch (Exception e) {
            // Defensive fallback: decompress the whole recording to disk first and disassemble
            // the plain file. A fresh output directory is used so partially written chunk files
            // from the failed streaming attempt cannot leak into the result.
            LOG.warn("Streaming LZ4 disassembly failed, falling back to eager decompression: recording={}",
                    recording, e);

            // Recovered, but not for free: the fallback writes the whole decompressed recording to
            // disk before reading any of it, so a parse that took twice as long and needed the space
            // has an explanation here rather than looking like an unexplained outlier.
            Notifications.of(NotificationType.RECORDING_DECOMPRESSION_FALLBACK)
                    .attribute("recording", String.valueOf(recording))
                    .errorType(e)
                    .emit();
            Path decompressed = lz4Compressor.decompressToDir(recording, tempDir.path());
            return JfrParser.disassemble(decompressed, tempDir.path().resolve(CHUNKS_FALLBACK_DIR));
        }
    }
}
