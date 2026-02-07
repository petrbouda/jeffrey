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

package pbouda.jeffrey.profile.parser.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.provider.profile.model.recording.RecordingInformation;

import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
 * <p>Supports both regular .jfr files and LZ4 compressed .jfr.lz4 files.</p>
 */
public abstract class JfrParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrParser.class);

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

    // ========== Recording Information ==========

    /**
     * Extracts recording information from a file.
     * Performs a single pass through all chunks to collect timing info and event types.
     *
     * @param recording the path to the recording file
     * @return recording information including size, timing, and event source
     */
    public static RecordingInformation recordingInfo(Path recording) {
        validateRecording(recording);
        List<JfrChunk> chunks = ChunkIterator.collect(recording);
        return buildRecordingInfo(chunks);
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

    // ========== Event Types ==========

    /**
     * Reads all event types from a recording file.
     *
     * @param recording the path to the recording file
     * @return set of event type names
     */
    public static Set<String> eventTypes(Path recording) {
        validateRecording(recording);
        Set<String> eventTypes = new HashSet<>();
        ChunkIterator.iterate(recording, (__, chunk) -> eventTypes.addAll(chunk.eventTypes()));
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
     * @param recording the path to the recording file
     * @param consumer  callback receiving FileChannel and JfrChunk for each chunk
     */
    public static void iterateChunks(Path recording, BiConsumer<FileChannel, JfrChunk> consumer) {
        validateRecording(recording);
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
        validateRecording(recording);
        return ChunkIterator.collect(recording);
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
        validateRecording(recording);
        ChunkIterator.iterateHeaders(recording, consumer);
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
        validateRecording(recording);
        return ChunkIterator.collectHeaders(recording);
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

        boolean anyProfilerEvent = chunks.stream()
                .flatMap(chunk -> chunk.eventTypes().stream())
                .anyMatch(eventType -> eventType.startsWith("profiler."));

        RecordingEventSource source = anyProfilerEvent
                ? RecordingEventSource.ASYNC_PROFILER
                : RecordingEventSource.JDK;

        return new RecordingInformation(bytes, source, startTime, endTime);
    }

    private static void validateRecording(Path recording) {
        if (!FileSystemUtils.isFile(recording)) {
            throw new IllegalArgumentException("Recording does not exist: " + recording);
        }
    }
}
