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

/**
 * Parses and validates the HPROF file header from a ByteBuffer.
 * <p>
 * The HPROF header format:
 * <pre>
 *   [null-terminated version string]  (e.g. "JAVA PROFILE 1.0.2\0")
 *   [u4 identifier size]             (4 or 8)
 *   [u4 high word of timestamp]
 *   [u4 low word of timestamp]
 * </pre>
 */
public final class HprofHeaderParser {

    private static final String VERSION_PREFIX = "JAVA PROFILE 1.0.";
    private static final int MAX_VERSION_LENGTH = 32;

    private HprofHeaderParser() {
    }

    /**
     * Parses the HPROF header from the given buffer starting at position 0.
     *
     * @param buffer the buffer containing the header bytes
     * @return the parsed header
     * @throws IOException if the header is invalid or truncated
     */
    public static HprofHeader parse(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < VERSION_PREFIX.length() + 2 + 4 + 8) {
            throw new IOException("HPROF header is truncated");
        }

        // Read null-terminated version string
        int start = buffer.position();
        int nullPos = -1;
        int limit = Math.min(buffer.limit(), start + MAX_VERSION_LENGTH);

        for (int i = start; i < limit; i++) {
            if (buffer.get(i) == 0) {
                nullPos = i;
                break;
            }
        }

        if (nullPos < 0) {
            throw new IOException("HPROF version string not null-terminated");
        }

        byte[] versionBytes = new byte[nullPos - start];
        buffer.get(versionBytes);
        String version = new String(versionBytes);

        if (!version.startsWith(VERSION_PREFIX)) {
            throw new IOException("Invalid HPROF version string: " + version);
        }

        // Skip the null terminator
        buffer.get();

        if (buffer.remaining() < 12) {
            throw new IOException("HPROF header is truncated after version string");
        }

        int idSize = buffer.getInt();
        if (idSize != 4 && idSize != 8) {
            throw new IOException("Invalid HPROF identifier size: " + idSize);
        }

        // Timestamp: two u4 words → high * 2^32 + low
        long highWord = Integer.toUnsignedLong(buffer.getInt());
        long lowWord = Integer.toUnsignedLong(buffer.getInt());
        long timestamp = (highWord << 32) | lowWord;

        int headerSize = buffer.position() - start;
        return new HprofHeader(version, idSize, timestamp, headerSize);
    }
}
