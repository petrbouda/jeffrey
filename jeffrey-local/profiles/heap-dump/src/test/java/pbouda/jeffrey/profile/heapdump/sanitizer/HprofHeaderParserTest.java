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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HprofHeaderParserTest {

    @Nested
    class ValidHeaders {

        @Test
        void parsesStandardHeader() throws IOException {
            ByteBuffer buffer = buildHeader("JAVA PROFILE 1.0.2", 8, 0L);
            HprofHeader header = HprofHeaderParser.parse(buffer);

            assertEquals("JAVA PROFILE 1.0.2", header.version());
            assertEquals(8, header.idSize());
            assertEquals(0L, header.timestamp());
        }

        @Test
        void parsesHeaderWithIdSize4() throws IOException {
            ByteBuffer buffer = buildHeader("JAVA PROFILE 1.0.1", 4, 12345678L);
            HprofHeader header = HprofHeaderParser.parse(buffer);

            assertEquals("JAVA PROFILE 1.0.1", header.version());
            assertEquals(4, header.idSize());
            assertEquals(12345678L, header.timestamp());
        }

        @Test
        void parsesHeaderWithTimestamp() throws IOException {
            long timestamp = 1700000000000L;
            ByteBuffer buffer = buildHeader("JAVA PROFILE 1.0.2", 8, timestamp);
            HprofHeader header = HprofHeaderParser.parse(buffer);

            assertEquals(timestamp, header.timestamp());
        }

        @Test
        void headerSizeMatchesByteCount() throws IOException {
            String version = "JAVA PROFILE 1.0.2";
            ByteBuffer buffer = buildHeader(version, 8, 0L);
            HprofHeader header = HprofHeaderParser.parse(buffer);

            // version + null + u4(idSize) + u4(hiTimestamp) + u4(loTimestamp)
            int expectedSize = version.length() + 1 + 4 + 8;
            assertEquals(expectedSize, header.headerSize());
        }
    }

    @Nested
    class InvalidHeaders {

        @Test
        void throwsOnTruncatedBuffer() {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            assertThrows(IOException.class, () -> HprofHeaderParser.parse(buffer));
        }

        @Test
        void throwsOnMissingNullTerminator() {
            byte[] versionBytes = "JAVA PROFILE 1.0.2".getBytes(StandardCharsets.US_ASCII);
            // Fill buffer with non-null characters - no terminator
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.put(versionBytes);
            for (int i = versionBytes.length; i < 64; i++) {
                buffer.put((byte) 'X');
            }
            buffer.flip();

            assertThrows(IOException.class, () -> HprofHeaderParser.parse(buffer));
        }

        @Test
        void throwsOnInvalidVersionPrefix() {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.put("NOT A VALID HEADER\0".getBytes(StandardCharsets.US_ASCII));
            buffer.putInt(8);
            buffer.putInt(0);
            buffer.putInt(0);
            buffer.flip();

            assertThrows(IOException.class, () -> HprofHeaderParser.parse(buffer));
        }

        @Test
        void throwsOnInvalidIdSize() {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.put("JAVA PROFILE 1.0.2\0".getBytes(StandardCharsets.US_ASCII));
            buffer.putInt(16); // Invalid ID size
            buffer.putInt(0);
            buffer.putInt(0);
            buffer.flip();

            assertThrows(IOException.class, () -> HprofHeaderParser.parse(buffer));
        }

        @Test
        void throwsOnTruncatedAfterVersion() {
            ByteBuffer buffer = ByteBuffer.allocate(25);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.put("JAVA PROFILE 1.0.2\0".getBytes(StandardCharsets.US_ASCII));
            // Only 5 bytes left, need 12
            buffer.flip();

            assertThrows(IOException.class, () -> HprofHeaderParser.parse(buffer));
        }
    }

    private ByteBuffer buildHeader(String version, int idSize, long timestamp) {
        byte[] versionBytes = version.getBytes(StandardCharsets.US_ASCII);
        int size = versionBytes.length + 1 + 4 + 8;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(versionBytes);
        buffer.put((byte) 0);
        buffer.putInt(idSize);
        buffer.putInt((int) (timestamp >>> 32));
        buffer.putInt((int) timestamp);
        buffer.flip();
        return buffer;
    }
}
