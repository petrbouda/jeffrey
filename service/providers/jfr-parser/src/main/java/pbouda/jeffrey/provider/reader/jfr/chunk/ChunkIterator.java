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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class ChunkIterator {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkIterator.class);

    private static final int CHUNK_HEADER_SIZE = 68;
    private static final int CHUNK_MAGIC = 0x464c5200;
    private static final int MASK_FINAL_CHUNK = 1 << 1;

    public static void iterate(Path recording, BiConsumer<FileChannel, JfrChunk> consumer) {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try (FileChannel channel = FileChannel.open(recording)) {
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

                if (header.magic() != CHUNK_MAGIC) {
                    LOG.error("Invalid chunk magic: {}", Integer.toHexString(header.magic()));
                    break;
                }

                if (header.version() < 0x20000 || header.version() > 0x2ffff) {
                    LOG.error("Unknown version: {}", Integer.toHexString(header.version()));
                    break;
                }

                if (header.offsetConstantPool() <= 0 || header.offsetMeta() <= 0) {
                    LOG.error("Invalid offsets: cp {} meta {}", header.offsetConstantPool(), header.offsetMeta());
                    break;
                }

                if (header.size() <= 0) {
                    LOG.error("Invalid size: {}", header.size());
                    break;
                }

                Set<String> eventTypes = EventTypeParser.extractEventTypes(channel, currentPosition, header);
                JfrChunk formattedHeader = formatChunk(header, eventTypes);
                consumer.accept(channel, formattedHeader);

                // Move to the next chunk
                buffer.clear();
                channel.position(currentPosition + header.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot split the recording by chunks: " + recording, e);
        }
    }

    private static JfrChunk formatChunk(RawChunkHeader header, Set<String> eventTypes) {
        Instant startTime = Instant.ofEpochSecond(
                header.startNanos() / 1_000_000_000,
                header.startNanos() % 1_000_000_000);

        Duration duration = Duration.ofNanos(header.durationNanos());
        boolean finalChunk = (header.features() & MASK_FINAL_CHUNK) != 0;
        return new JfrChunk(startTime, duration, header.size(), eventTypes, finalChunk);
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
