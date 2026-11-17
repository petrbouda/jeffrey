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
