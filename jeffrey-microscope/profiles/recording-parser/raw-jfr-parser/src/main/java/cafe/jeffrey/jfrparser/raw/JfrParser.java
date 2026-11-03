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

package cafe.jeffrey.jfrparser.raw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.microscope.model.EventSourceResolver;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;
import cafe.jeffrey.provider.profile.api.RecordingInformation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Facade for JFR (Java Flight Recording) parsing operations.
 * Provides a clean API for:
 * <ul>
 *   <li>Disassembling recordings into individual chunks</li>
 *   <li>Extracting recording information (timing, size, event source)</li>
 *   <li>Reading event types from recordings</li>
 *   <li>Iterating over chunks for custom processing</li>
 * </ul>
 *
 * <p>Every method taking a {@link Path} reads both regular .jfr files and LZ4 compressed .jfr.lz4
 * files, but {@link #iterateChunks(Path, BiConsumer)}, whose consumer reads the file through a
 * channel and so needs a plain one.</p>
 */
public abstract class JfrParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrParser.class);

    private static final String LZ4_HAS_NO_CHANNEL = "Recording is LZ4-compressed, and a file channel reads only "
            + "a plain .jfr: iterate it through Lz4Compressor.decompressStream instead: path=%s";

    // ========== Disassembly ==========

    /**
     * Splits a recording into individual chunk files.
     * Supports both plain .jfr and LZ4 compressed .jfr.lz4 files.
     *
     * @param recording the path to the recording file
     * @param outputDir the directory to write chunk files to
     * @return list of paths to the created chunk files
     */
    public static List<Path> disassemble(Path recording, Path outputDir) {
        validateRecording(recording);
        return RecordingDisassembler.disassemble(recording, outputDir);
    }

    /**
     * Disassembles a recording, handing each chunk to {@code onChunkWritten} as soon as it is
     * written — so a caller can start parsing chunk N while chunk N+1 is still being copied.
     */
    public static List<Path> disassemble(Path recording, Path outputDir, Consumer<Path> onChunkWritten) {
        validateRecording(recording);
        return RecordingDisassembler.disassemble(recording, outputDir, onChunkWritten);
    }

    // ========== Recording Information ==========

    /**
     * Extracts recording information from a file.
     * Performs a single pass through all chunks to collect timing info and event types.
     *
     * @param recording the path to the recording file
     * @return recording information including size, timing, and event source
     */
    public static RecordingInformation recordingInfo(Path recording) {
        return buildRecordingInfo(read(recording, ChunkIterator::collect, ChunkIterator::collect));
    }

    /**
     * Extracts recording information from an input stream.
     * Supports streaming parsing (e.g., for LZ4 compressed streams).
     *
     * @param input InputStream containing JFR data
     * @return recording information including size, timing, and event source
     */
    public static RecordingInformation recordingInfo(InputStream input) {
        List<JfrChunk> chunks = ChunkIterator.collect(input);
        return buildRecordingInfo(chunks);
    }

    /**
     * Extracts recording information from chunks already collected elsewhere.
     * <p>
     * A recording made of several files is described by pooling their chunks and asking this
     * once: the window is then the earliest start and the latest end across all of them, which is
     * what the recording actually covers. Collecting the chunks is left to the caller because
     * only it knows how to open each file — LZ4 or plain.
     *
     * @param chunks every chunk of the recording, in any order
     * @return recording information including size, timing, and event source
     */
    public static RecordingInformation recordingInfo(List<JfrChunk> chunks) {
        return buildRecordingInfo(chunks);
    }

    // ========== Event Types ==========

    /**
     * Reads all event types from a recording file.
     *
     * @param recording the path to the recording file
     * @return set of event type names
     */
    public static Set<String> eventTypes(Path recording) {
        Set<String> eventTypes = new HashSet<>();
        for (JfrChunk chunk : collectChunks(recording)) {
            eventTypes.addAll(chunk.eventTypes());
        }
        return eventTypes;
    }

    /**
     * Reads all event types from an input stream.
     *
     * @param input InputStream containing JFR data
     * @return set of event type names
     */
    public static Set<String> eventTypes(InputStream input) {
        Set<String> eventTypes = new HashSet<>();
        ChunkIterator.iterate(input, chunk -> eventTypes.addAll(chunk.eventTypes()));
        return eventTypes;
    }

    // ========== Chunk Iteration ==========

    /**
     * Iterates over all chunks in a recording with full event type parsing.
     *
     * @param recording the path to a plain {@code .jfr} file: the consumer reads it through a file
     *                  channel, which cannot read a compressed one
     * @param consumer  callback receiving FileChannel and JfrChunk for each chunk
     * @throws IllegalArgumentException for an LZ4-compressed recording, which reads through
     *                                  {@link #iterateChunks(InputStream, Consumer)} instead
     */
    public static void iterateChunks(Path recording, BiConsumer<FileChannel, JfrChunk> consumer) {
        validateRecording(recording);
        if (Lz4Compressor.isLz4Compressed(recording)) {
            throw new IllegalArgumentException(LZ4_HAS_NO_CHANNEL.formatted(recording));
        }
        ChunkIterator.iterate(recording, consumer);
    }

    /**
     * Iterates over all chunks from an input stream with full event type parsing.
     *
     * @param input    InputStream containing JFR data
     * @param consumer callback receiving JfrChunk for each chunk
     */
    public static void iterateChunks(InputStream input, Consumer<JfrChunk> consumer) {
        ChunkIterator.iterate(input, consumer);
    }

    /**
     * Collects all chunks from a recording with full event type parsing.
     *
     * @param recording the path to the recording file
     * @return list of all chunks
     */
    public static List<JfrChunk> collectChunks(Path recording) {
        return read(recording, ChunkIterator::collect, ChunkIterator::collect);
    }

    /**
     * Collects all chunks from an input stream with full event type parsing.
     *
     * @param input InputStream containing JFR data
     * @return list of all chunks
     */
    public static List<JfrChunk> collectChunks(InputStream input) {
        return ChunkIterator.collect(input);
    }

    // ========== Header-only Iteration (efficient) ==========

    /**
     * Iterates over chunk headers without parsing event types.
     * More efficient when only timing/size information is needed.
     *
     * @param recording the path to the recording file
     * @param consumer  callback receiving JfrChunkHeader for each chunk
     */
    public static void iterateHeaders(Path recording, Consumer<JfrChunkHeader> consumer) {
        collectHeaders(recording).forEach(consumer);
    }

    /**
     * Iterates over chunk headers from an input stream without parsing event types.
     *
     * @param input    InputStream containing JFR data
     * @param consumer callback receiving JfrChunkHeader for each chunk
     */
    public static void iterateHeaders(InputStream input, Consumer<JfrChunkHeader> consumer) {
        ChunkIterator.iterateHeaders(input, consumer);
    }

    /**
     * Collects all chunk headers without parsing event types.
     *
     * @param recording the path to the recording file
     * @return list of chunk headers
     */
    public static List<JfrChunkHeader> collectHeaders(Path recording) {
        return read(recording, ChunkIterator::collectHeaders, ChunkIterator::collectHeaders);
    }

    /**
     * Collects all chunk headers from an input stream without parsing event types.
     *
     * @param input InputStream containing JFR data
     * @return list of chunk headers
     */
    public static List<JfrChunkHeader> collectHeaders(InputStream input) {
        return ChunkIterator.collectHeaders(input);
    }

    // ========== Internal Helpers ==========

    private static RecordingInformation buildRecordingInfo(List<JfrChunk> chunks) {
        if (chunks.isEmpty()) {
            LOG.warn("Recording does not contain any chunks");

            // An empty profile is indistinguishable from one whose application did nothing, and the
            // two want completely different next steps from whoever is looking at it.
            Notifications.of(NotificationType.RECORDING_HAS_NO_CHUNKS)
                    .emit();
            return new RecordingInformation(0, RecordingEventSource.JDK, null, null);
        }

        long bytes = chunks.stream().mapToLong(JfrChunk::sizeInBytes).sum();

        Instant startTime = chunks.stream()
                .map(JfrChunk::startTime)
                .min(Instant::compareTo)
                .orElse(null);

        Instant endTime = chunks.stream()
                .map(chunk -> chunk.startTime().plus(chunk.duration()))
                .max(Instant::compareTo)
                .orElse(null);

        List<String> eventTypeNames = chunks.stream()
                .flatMap(chunk -> chunk.eventTypes().stream())
                .toList();

        RecordingEventSource source = EventSourceResolver.fromEventTypeNames(eventTypeNames);

        return new RecordingInformation(bytes, source, startTime, endTime);
    }

    /**
     * Reads a recording file as it lies when it is a plain {@code .jfr}, and through a decompressing
     * stream when it is LZ4-compressed: read as it lies, an archive has no JFR header to find, and
     * would read as a recording with no chunks rather than fail.
     */
    private static <T> T read(Path recording, Function<Path, T> plain, Function<InputStream, T> compressed) {
        validateRecording(recording);
        if (!Lz4Compressor.isLz4Compressed(recording)) {
            return plain.apply(recording);
        }
        try (InputStream input = Lz4Compressor.decompressStream(recording)) {
            return compressed.apply(input);
        } catch (IOException e) {
            throw new JfrChunkParsingException("Failed to read LZ4 recording: " + recording, e);
        }
    }

    private static void validateRecording(Path recording) {
        if (!FileSystemUtils.isFile(recording)) {
            throw new IllegalArgumentException("Recording does not exist: " + recording);
        }
    }
}
