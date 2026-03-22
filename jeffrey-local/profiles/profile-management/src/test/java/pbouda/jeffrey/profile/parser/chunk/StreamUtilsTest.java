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

package pbouda.jeffrey.profile.parser.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamUtils")
class StreamUtilsTest {

    @Nested
    @DisplayName("readFully")
    class ReadFully {

        @Nested
        @DisplayName("when stream has exactly the requested number of bytes")
        class ReadFullyExact {

            @Test
            @DisplayName("reads all bytes and returns the full count")
            void readsAllBytes() throws IOException {
                byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
                ByteArrayInputStream input = new ByteArrayInputStream(data);

                byte[] buffer = new byte[10];
                int bytesRead = StreamUtils.readFully(input, buffer, 0, 10);

                assertEquals(10, bytesRead);
                assertArrayEquals(data, buffer);
            }
        }

        @Nested
        @DisplayName("when stream has fewer bytes than requested")
        class ReadFullyPartial {

            @Test
            @DisplayName("returns the actual number of bytes read")
            void returnsPartialCount() throws IOException {
                byte[] data = {10, 20, 30, 40, 50};
                ByteArrayInputStream input = new ByteArrayInputStream(data);

                byte[] buffer = new byte[10];
                int bytesRead = StreamUtils.readFully(input, buffer, 0, 10);

                assertEquals(5, bytesRead);
                for (int i = 0; i < 5; i++) {
                    assertEquals(data[i], buffer[i]);
                }
            }
        }

        @Nested
        @DisplayName("when stream is empty")
        class ReadFullyEmpty {

            @Test
            @DisplayName("returns zero bytes read")
            void returnsZero() throws IOException {
                ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);

                byte[] buffer = new byte[10];
                int bytesRead = StreamUtils.readFully(input, buffer, 0, 10);

                assertEquals(0, bytesRead);
            }
        }

        @Nested
        @DisplayName("when reading with an offset into the buffer")
        class ReadFullyWithOffset {

            @Test
            @DisplayName("writes bytes at the specified offset")
            void writesAtOffset() throws IOException {
                byte[] data = {1, 2, 3, 4, 5};
                ByteArrayInputStream input = new ByteArrayInputStream(data);

                byte[] buffer = new byte[10];
                int bytesRead = StreamUtils.readFully(input, buffer, 2, 3);

                assertEquals(3, bytesRead);
                assertEquals(1, buffer[2]);
                assertEquals(2, buffer[3]);
                assertEquals(3, buffer[4]);
                // Positions outside the written range should remain zero
                assertEquals(0, buffer[0]);
                assertEquals(0, buffer[1]);
                assertEquals(0, buffer[5]);
            }
        }
    }

    @Nested
    @DisplayName("skipFully")
    class SkipFully {

        @Nested
        @DisplayName("when stream has enough bytes to skip")
        class SkipFullyExact {

            @Test
            @DisplayName("skips the requested bytes and allows reading the remainder")
            void skipsAndReadsRemaining() throws IOException {
                byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
                ByteArrayInputStream input = new ByteArrayInputStream(data);

                StreamUtils.skipFully(input, 5);

                byte[] remaining = new byte[5];
                int bytesRead = StreamUtils.readFully(input, remaining, 0, 5);

                assertEquals(5, bytesRead);
                assertArrayEquals(new byte[]{5, 6, 7, 8, 9}, remaining);
            }
        }

        @Nested
        @DisplayName("when stream has fewer bytes than requested to skip")
        class SkipFullyEof {

            @Test
            @DisplayName("throws EOFException")
            void throwsEofException() {
                byte[] data = {1, 2, 3};
                ByteArrayInputStream input = new ByteArrayInputStream(data);

                assertThrows(EOFException.class, () -> StreamUtils.skipFully(input, 10));
            }
        }

        @Nested
        @DisplayName("when skipping zero bytes")
        class SkipFullyZero {

            @Test
            @DisplayName("completes without exception")
            void doesNotThrow() throws IOException {
                byte[] data = {1, 2, 3};
                ByteArrayInputStream input = new ByteArrayInputStream(data);

                assertDoesNotThrow(() -> StreamUtils.skipFully(input, 0));
            }
        }
    }
}
