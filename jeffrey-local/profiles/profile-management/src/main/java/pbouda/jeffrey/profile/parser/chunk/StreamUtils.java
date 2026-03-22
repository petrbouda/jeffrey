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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for stream operations used in JFR chunk parsing.
 */
public abstract class StreamUtils {

    /**
     * Reads exactly the specified number of bytes, or returns actual bytes read if EOF.
     *
     * @param input  the input stream to read from
     * @param buffer the buffer to read into
     * @param offset the offset in the buffer to start writing
     * @param length the number of bytes to read
     * @return the number of bytes actually read (may be less than length if EOF)
     * @throws IOException if an I/O error occurs
     */
    public static int readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = input.read(buffer, offset + totalRead, length - totalRead);
            if (read < 0) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }

    /**
     * Skips exactly the specified number of bytes from the input stream.
     *
     * @param input  the input stream to skip from
     * @param toSkip the number of bytes to skip
     * @throws IOException  if an I/O error occurs
     * @throws EOFException if EOF is reached before skipping all bytes
     */
    public static void skipFully(InputStream input, long toSkip) throws IOException {
        long remaining = toSkip;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                // Fallback: read and discard
                byte[] buf = new byte[(int) Math.min(8192, remaining)];
                int read = input.read(buf);
                if (read < 0) {
                    throw new EOFException("Unexpected EOF while skipping " + remaining + " bytes");
                }
                skipped = read;
            }
            remaining -= skipped;
        }
    }
}
