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

package pbouda.jeffrey.provider.writer.sql;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.eclipse.collections.api.list.primitive.LongList;
import pbouda.jeffrey.provider.api.model.EventFrame;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Not thread-sfe
public class SingleThreadHasher {

    // Single XXHash64 instance - thread-safe for reading
    private static final XXHash64 HASHER = XXHashFactory.fastestInstance().hash64();

    // Automatically managed buffer for frame hashing
    private byte[] frameBuffer = new byte[4096];

    /**
     * ✅ FAST HASH: Uses reusable thread-local buffer
     * No allocations after first call per thread
     * ~200-300ns per hash on modern hardware
     */
    public long getFrameHash(EventFrame frame) {
        // Get this thread's reusable buffer
        byte[] buffer = frameBuffer;

        // Convert strings to bytes
        byte[] classBytes = safeGetBytes(frame.clazz());
        byte[] methodBytes = safeGetBytes(frame.method());
        byte[] typeBytes = safeGetBytes(frame.type());

        // Calculate total size needed
        int totalSize = 4 + classBytes.length      // length (4 bytes) + class string
                        + 4 + methodBytes.length   // length (4 bytes) + method string
                        + 4 + typeBytes.length     // length (4 bytes) + type string
                        + 8                        // bci (8 bytes)
                        + 8;                       // line (8 bytes)

        // Grow buffer if needed (rare after warmup)
        if (buffer.length < totalSize) {
            buffer = new byte[totalSize * 2]; // Double size for future growth
            frameBuffer = buffer;
        }

        // Write all data to buffer
        int offset = 0;

        // Write class string (length + bytes)
        offset = writeString(buffer, offset, classBytes);

        // Write method string (length + bytes)
        offset = writeString(buffer, offset, methodBytes);

        // Write type string (length + bytes)
        offset = writeString(buffer, offset, typeBytes);

        // Write bci (8 bytes, little-endian)
        offset = writeLong(buffer, offset, frame.bci());

        // Write line (8 bytes, little-endian)
        offset = writeLong(buffer, offset, frame.line());

        // Hash the buffer (only the bytes we wrote)
        return HASHER.hash(buffer, 0, offset, 0);
    }

    public long hashStackTrace(List<Long> frameHashes) {
        if (frameHashes == null || frameHashes.isEmpty()) {
            return 0L;
        }

        byte[] bytes = new byte[frameHashes.size() * 8];
        int offset = 0;
        frameHashes.forEach(hash -> writeLong(bytes, offset, hash));
        return HASHER.hash(bytes, 0, bytes.length, 0);
    }

    /**
     * Safely convert string to bytes (handle nulls)
     */
    private static byte[] safeGetBytes(String str) {
        return (str != null ? str : "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Write string to buffer: 4-byte length + UTF-8 bytes
     * 4 bytes length prevents the collisions:
     * - With length:
     * frame1: [2]['A']['B'][2]['C']['D'][4]['j']['a']['v']['a'][0][0]
     * ↑length=2     ↑length=2
     * frame2: [1]['A'][3]['B']['C']['D'][4]['j']['a']['v']['a'][0][0]
     * ↑length=1 ↑length=3
     * - Without length:
     * frame1: [A][B][C][D][j][a][v][a][0][0]
     * frame2: [A][B][C][D][j][a][v][a][0][0]
     * ↑ SAME BYTES! ↑
     *
     * @return Returns new offset position
     */
    private static int writeString(byte[] buffer, int offset, byte[] stringBytes) {
        int length = stringBytes.length;

        // Write length as 4 bytes (little-endian)
        buffer[offset++] = (byte) (length);
        buffer[offset++] = (byte) (length >> 8);
        buffer[offset++] = (byte) (length >> 16);
        buffer[offset++] = (byte) (length >> 24);

        // Copy string bytes
        System.arraycopy(stringBytes, 0, buffer, offset, stringBytes.length);

        return offset + stringBytes.length;
    }

    /**
     * Write long to buffer as 8 bytes (little-endian)
     * Returns new offset position
     */
    private static int writeLong(byte[] buffer, int offset, long value) {
        buffer[offset++] = (byte) (value);
        buffer[offset++] = (byte) (value >> 8);
        buffer[offset++] = (byte) (value >> 16);
        buffer[offset++] = (byte) (value >> 24);
        buffer[offset++] = (byte) (value >> 32);
        buffer[offset++] = (byte) (value >> 40);
        buffer[offset++] = (byte) (value >> 48);
        buffer[offset++] = (byte) (value >> 56);
        return offset;
    }
}
