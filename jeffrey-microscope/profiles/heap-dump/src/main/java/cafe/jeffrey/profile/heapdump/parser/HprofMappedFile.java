/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
package cafe.jeffrey.profile.heapdump.parser;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Random-access reader over an HPROF file, backed by a single shared {@link MemorySegment}.
 *
 * Replaces the synchronized-{@code ByteBuffer} approach used by the NetBeans parser.
 * All accessors are stateless and thread-safe; multiple worker threads may read
 * concurrently from the same instance without contention.
 *
 * The header is parsed eagerly at construction.
 */
public final class HprofMappedFile implements AutoCloseable {

    private static final int MAX_MAGIC_LEN = 32;

    private static final ValueLayout.OfByte LE_BYTE = ValueLayout.JAVA_BYTE;
    private static final ValueLayout.OfShort BE_SHORT = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt BE_INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong BE_LONG = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final Path path;
    private final Arena arena;
    private final MemorySegment segment;
    private final long size;
    private final HprofHeader header;

    private HprofMappedFile(Path path, Arena arena, MemorySegment segment, long size, HprofHeader header) {
        this.path = path;
        this.arena = arena;
        this.segment = segment;
        this.size = size;
        this.header = header;
    }

    public static HprofMappedFile open(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (!Files.exists(path)) {
            throw new IOException("Heap dump file does not exist: path=" + path);
        }
        long size = Files.size(path);
        if (size < HprofHeader.MAGIC_1_0_1.length() + 1 + 4 + 8) {
            throw new IOException("Heap dump file too small to contain a valid header: path=" + path + " size=" + size);
        }

        Arena arena = Arena.ofShared();
        try {
            MemorySegment segment;
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
            }
            HprofHeader header = readHeader(segment, size);
            return new HprofMappedFile(path, arena, segment, size, header);
        } catch (Throwable t) {
            arena.close();
            throw t;
        }
    }

    public Path path() {
        return path;
    }

    public long size() {
        return size;
    }

    public HprofHeader header() {
        return header;
    }

    public byte readByte(long offset) {
        return segment.get(LE_BYTE, offset);
    }

    public short readShort(long offset) {
        return segment.get(BE_SHORT, offset);
    }

    public int readInt(long offset) {
        return segment.get(BE_INT, offset);
    }

    public long readLong(long offset) {
        return segment.get(BE_LONG, offset);
    }

    /**
     * Reads an HPROF object identifier at {@code offset}. ID width depends on the
     * file's header ({@code idSize}). For 4-byte IDs the value is zero-extended.
     *
     * Performs a runtime branch on every call; specialised variants come in a
     * later phase once the hot scan is profiled.
     */
    public long readId(long offset) {
        if (header.idSize() == 4) {
            return segment.get(BE_INT, offset) & 0xFFFFFFFFL;
        }
        return segment.get(BE_LONG, offset);
    }

    /** Reads {@code length} bytes starting at {@code offset} into a fresh byte array. */
    public byte[] readBytes(long offset, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative: length=" + length);
        }
        byte[] dst = new byte[length];
        MemorySegment.copy(segment, LE_BYTE, offset, dst, 0, length);
        return dst;
    }

    /** Reads {@code length} bytes starting at {@code offset} into {@code dst} at {@code dstOffset}. */
    public void readBytes(long offset, byte[] dst, int dstOffset, int length) {
        MemorySegment.copy(segment, LE_BYTE, offset, dst, dstOffset, length);
    }

    @Override
    public void close() {
        arena.close();
    }

    private static HprofHeader readHeader(MemorySegment segment, long size) throws IOException {
        StringBuilder magic = new StringBuilder();
        long offset = 0;
        while (offset < size && offset < MAX_MAGIC_LEN) {
            byte b = segment.get(LE_BYTE, offset);
            offset++;
            if (b == 0) {
                break;
            }
            magic.append((char) (b & 0xFF));
        }
        String version = switch (magic.toString()) {
            case HprofHeader.MAGIC_1_0_1 -> "1.0.1";
            case HprofHeader.MAGIC_1_0_2 -> "1.0.2";
            case HprofHeader.MAGIC_1_0_3 -> "1.0.3";
            default -> throw new IOException("Unrecognised HPROF magic: magic=\"" + magic + "\"");
        };

        if (offset + 4 + 8 > size) {
            throw new IOException("Heap dump truncated within header: size=" + size + " headerOffset=" + offset);
        }

        int idSize = segment.get(BE_INT, offset);
        offset += 4;
        long timestampMs = segment.get(BE_LONG, offset);
        offset += 8;

        return new HprofHeader(version, idSize, timestampMs, offset);
    }
}
