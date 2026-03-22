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

package pbouda.jeffrey.profile.heapdump.sanitizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Reads 9-byte top-level HPROF record headers from a FileChannel.
 * <p>
 * Each top-level record consists of:
 * <pre>
 *   [u1 tag]
 *   [u4 timestamp delta]
 *   [u4 body length]
 *   [body bytes...]
 * </pre>
 */
public final class HprofRecordReader {

    /**
     * A parsed top-level record header.
     */
    public record RecordHeader(int tag, int timestampDelta, int bodyLength, long fileOffset) {

        /**
         * Returns the body length as an unsigned long.
         */
        public long unsignedBodyLength() {
            return Integer.toUnsignedLong(bodyLength);
        }
    }

    private static final int RECORD_HEADER_SIZE = 9;

    private HprofRecordReader() {
    }

    /**
     * Reads the next record header from the channel.
     *
     * @param channel the file channel positioned at the start of a record
     * @param buffer  a reusable ByteBuffer (must have capacity >= 9)
     * @return the record header, or null at EOF
     * @throws IOException if the header is truncated or an I/O error occurs
     */
    public static RecordHeader readHeader(FileChannel channel, ByteBuffer buffer) throws IOException {
        long fileOffset = channel.position();

        buffer.clear();
        buffer.limit(RECORD_HEADER_SIZE);

        int totalRead = 0;
        while (totalRead < RECORD_HEADER_SIZE) {
            int read = channel.read(buffer);
            if (read < 0) {
                if (totalRead == 0) {
                    return null; // Clean EOF
                }
                throw new IOException("Truncated record header at offset " + fileOffset);
            }
            totalRead += read;
        }

        buffer.flip();

        int tag = Byte.toUnsignedInt(buffer.get());
        int timestampDelta = buffer.getInt();
        int bodyLength = buffer.getInt();

        return new RecordHeader(tag, timestampDelta, bodyLength, fileOffset);
    }
}
