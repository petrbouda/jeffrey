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

package pbouda.jeffrey.provider.reader.jfr.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Iterator for JFR chunks supporting both file-based and streaming modes.
 * Provides header-only iteration (efficient) and full iteration with event types.
 */
public abstract class ChunkIterator implements JfrChunkConstants {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkIterator.class);

    // ========== File-based iteration with full event types ==========

    /**
     * Iterates over all chunks with full event type parsing.
     *
     * @param recording Path to JFR file
     * @param consumer  Callback receiving FileChannel and JfrChunk with event types
     */
    public static void iterate(Path recording, BiConsumer<FileChannel, JfrChunk> consumer) {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try (FileChannel channel = FileChannel.open(recording)) {
            LOG.debug("Starting to iterate over chunks in recording: {}", recording);

            long recordingSize = Files.size(recording);
            LOG.trace("Recording size: recording={} size={}", recording.getFileName(), recordingSize);

            while (channel.position() + CHUNK_HEADER_SIZE <= recordingSize) {
                long currentPosition = channel.position();
                int read = channel.read(buffer);

                if (read < CHUNK_HEADER_SIZE) {
                    LOG.error("Failed to read chunk header");
                    break;
                }

                buffer.flip();
                RawChunkHeader header = readChunkHeader(buffer);
                LOG.trace("Read chunk header: recording={} header={}", recording.getFileName(), header);

                if (!isValidHeader(header)) {
                    break;
                }

                Set<String> eventTypes = EventTypeParser.extractEventTypes(channel, currentPosition, header);
                JfrChunk formattedChunk = formatChunkHeader(header).withEventTypes(eventTypes);
                consumer.accept(channel, formattedChunk);

                // Move to the next chunk
                buffer.clear();
                channel.position(currentPosition + header.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot iterate over chunks in recording: " + recording, e);
        }
    }

    /**
     * Collects all chunks with full event type parsing.
     *
     * @param recording Path to JFR file
     * @return List of JfrChunk with event types
     */
    public static List<JfrChunk> collect(Path recording) {
        List<JfrChunk> chunks = new ArrayList<>();
        iterate(recording, (_, chunk) -> chunks.add(chunk));
        return chunks;
    }

    // ========== File-based header-only iteration (efficient) ==========

    /**
     * Iterates over chunk headers without parsing event types.
     * More efficient when only timing/size information is needed.
     *
     * @param recording Path to JFR file
     * @param consumer  Callback receiving JfrChunkHeader
     */
    public static void iterateHeaders(Path recording, Consumer<JfrChunkHeader> consumer) {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try (FileChannel channel = FileChannel.open(recording)) {
            LOG.debug("Starting header-only iteration over chunks in recording: {}", recording);

            long recordingSize = Files.size(recording);

            while (channel.position() + CHUNK_HEADER_SIZE <= recordingSize) {
                long currentPosition = channel.position();
                int read = channel.read(buffer);

                if (read < CHUNK_HEADER_SIZE) {
                    LOG.error("Failed to read chunk header");
                    break;
                }

                buffer.flip();
                RawChunkHeader header = readChunkHeader(buffer);

                if (!isValidHeader(header)) {
                    break;
                }

                JfrChunkHeader chunkHeader = formatChunkHeader(header);
                consumer.accept(chunkHeader);

                // Move to the next chunk
                buffer.clear();
                channel.position(currentPosition + header.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot iterate over chunk headers in recording: " + recording, e);
        }
    }

    /**
     * Collects all chunk headers without parsing event types.
     *
     * @param recording Path to JFR file
     * @return List of chunk headers
     */
    public static List<JfrChunkHeader> collectHeaders(Path recording) {
        List<JfrChunkHeader> headers = new ArrayList<>();
        iterateHeaders(recording, headers::add);
        return headers;
    }

    // ========== Streaming iteration with full event types ==========

    /**
     * Iterates over all chunks from an InputStream with full event type parsing.
     * Supports LZ4 compressed streams.
     *
     * @param input    InputStream containing JFR data
     * @param consumer Callback receiving JfrChunk with event types
     */
    public static void iterate(InputStream input, Consumer<JfrChunk> consumer) {
        byte[] headerBytes = new byte[CHUNK_HEADER_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try {
            while (true) {
                // Read header
                int totalRead = StreamUtils.readFully(input, headerBytes, 0, CHUNK_HEADER_SIZE);
                if (totalRead == 0) {
                    break; // Clean EOF at chunk boundary
                }
                if (totalRead < CHUNK_HEADER_SIZE) {
                    LOG.error("Incomplete chunk header read: {} bytes", totalRead);
                    break;
                }

                buffer.rewind();
                RawChunkHeader header = readChunkHeader(buffer);

                if (!isValidHeader(header)) {
                    throw new RuntimeException("Invalid chunk header in stream");
                }

                JfrChunkHeader chunkHeader = formatChunkHeader(header);

                // Extract event types using streaming parser
                EventTypeParser.EventTypeResult result = EventTypeParser.extractEventTypesStreaming(input, chunkHeader);
                JfrChunk chunk = chunkHeader.withEventTypes(result.eventTypes());

                consumer.accept(chunk);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot iterate over chunks from stream", e);
        }
    }

    /**
     * Collects all chunks from an InputStream with full event type parsing.
     *
     * @param input InputStream containing JFR data
     * @return List of JfrChunk with event types
     */
    public static List<JfrChunk> collect(InputStream input) {
        List<JfrChunk> chunks = new ArrayList<>();
        iterate(input, chunks::add);
        return chunks;
    }

    // ========== Streaming header-only iteration (fastest) ==========

    /**
     * Iterates over chunk headers from an InputStream without parsing event types.
     * This is the fastest streaming mode, useful when only timing info is needed.
     *
     * @param input    InputStream containing JFR data
     * @param consumer Callback receiving JfrChunkHeader
     */
    public static void iterateHeaders(InputStream input, Consumer<JfrChunkHeader> consumer) {
        byte[] headerBytes = new byte[CHUNK_HEADER_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try {
            while (true) {
                // Read header
                int totalRead = StreamUtils.readFully(input, headerBytes, 0, CHUNK_HEADER_SIZE);
                if (totalRead == 0) {
                    break; // Clean EOF at chunk boundary
                }
                if (totalRead < CHUNK_HEADER_SIZE) {
                    LOG.error("Incomplete chunk header read: {} bytes", totalRead);
                    break;
                }

                buffer.rewind();
                RawChunkHeader header = readChunkHeader(buffer);

                if (!isValidHeader(header)) {
                    throw new RuntimeException("Invalid chunk header in stream");
                }

                JfrChunkHeader chunkHeader = formatChunkHeader(header);
                consumer.accept(chunkHeader);

                // Skip remaining chunk data (size includes header)
                long toSkip = header.size() - CHUNK_HEADER_SIZE;
                StreamUtils.skipFully(input, toSkip);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot iterate over chunk headers from stream", e);
        }
    }

    /**
     * Collects all chunk headers from an InputStream without parsing event types.
     *
     * @param input InputStream containing JFR data
     * @return List of chunk headers
     */
    public static List<JfrChunkHeader> collectHeaders(InputStream input) {
        List<JfrChunkHeader> headers = new ArrayList<>();
        iterateHeaders(input, headers::add);
        return headers;
    }

    // ========== Helper methods ==========

    /**
     * Validates a raw chunk header.
     *
     * @param header the header to validate
     * @return true if valid, false otherwise
     */
    private static boolean isValidHeader(RawChunkHeader header) {
        if (header.magic() != CHUNK_MAGIC) {
            LOG.error("Invalid chunk magic: {}", Integer.toHexString(header.magic()));
            return false;
        }

        if (header.version() < 0x20000 || header.version() > 0x2ffff) {
            LOG.error("Unknown version: {}", Integer.toHexString(header.version()));
            return false;
        }

        if (header.offsetConstantPool() <= 0 || header.offsetMeta() <= 0) {
            LOG.error("Invalid offsets: cp {} meta {}", header.offsetConstantPool(), header.offsetMeta());
            return false;
        }

        if (header.size() <= 0) {
            LOG.error("Invalid size: {}", header.size());
            return false;
        }

        return true;
    }

    /**
     * Formats a raw chunk header into a JfrChunkHeader.
     *
     * @param header the raw header
     * @return formatted JfrChunkHeader
     */
    private static JfrChunkHeader formatChunkHeader(RawChunkHeader header) {
        Instant startTime = Instant.ofEpochSecond(
                header.startNanos() / 1_000_000_000,
                header.startNanos() % 1_000_000_000);

        Duration duration = Duration.ofNanos(header.durationNanos());
        boolean finalChunk = (header.features() & MASK_FINAL_CHUNK) != 0;
        return new JfrChunkHeader(startTime, duration, header.size(), header.offsetMeta(), finalChunk);
    }

    private static RawChunkHeader readChunkHeader(ByteBuffer buffer) {
        return new RawChunkHeader(
                buffer.getInt(),
                buffer.getInt(),
                buffer.getLong(),
                buffer.getLong(),
                buffer.getLong(),
                buffer.getLong(),
                buffer.getLong(),
                buffer.getLong(),
                buffer.getLong(),
                buffer.getInt());
    }
}
