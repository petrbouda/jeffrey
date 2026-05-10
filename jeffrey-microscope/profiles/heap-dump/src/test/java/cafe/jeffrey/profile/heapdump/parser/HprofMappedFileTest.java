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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HprofMappedFileTest {

    private static final long FAKE_TIMESTAMP_MS = 1_700_000_000_000L;

    @Nested
    class Header {

        @Test
        void parsesValid_1_0_2_HeaderWithIdSize8(@TempDir Path tmp) throws IOException {
            Path hprof = writeHprofWithHeader(tmp, HprofHeader.MAGIC_1_0_2, 8, FAKE_TIMESTAMP_MS);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofHeader header = file.header();
                assertEquals("1.0.2", header.version());
                assertEquals(8, header.idSize());
                assertEquals(FAKE_TIMESTAMP_MS, header.timestampMs());
                assertEquals(HprofHeader.MAGIC_1_0_2.length() + 1 + 4 + 8, header.headerSize());
                assertEquals(Files.size(hprof), file.size());
            }
        }

        @Test
        void parsesValid_1_0_1_HeaderWithIdSize4(@TempDir Path tmp) throws IOException {
            Path hprof = writeHprofWithHeader(tmp, HprofHeader.MAGIC_1_0_1, 4, 42L);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                assertEquals("1.0.1", file.header().version());
                assertEquals(4, file.header().idSize());
                assertEquals(42L, file.header().timestampMs());
            }
        }

        @Test
        void rejectsUnknownMagic(@TempDir Path tmp) throws IOException {
            Path hprof = writeHprofWithHeader(tmp, "JAVA PROFILE 9.9.9", 8, 0L);

            IOException ex = assertThrows(IOException.class, () -> HprofMappedFile.open(hprof));
            assertEquals(true, ex.getMessage().contains("Unrecognised HPROF magic"));
        }

        @Test
        void rejectsTooSmallFile(@TempDir Path tmp) throws IOException {
            Path hprof = tmp.resolve("tiny.hprof");
            Files.write(hprof, new byte[]{1, 2, 3, 4});

            assertThrows(IOException.class, () -> HprofMappedFile.open(hprof));
        }

        @Test
        void rejectsMissingFile(@TempDir Path tmp) {
            Path missing = tmp.resolve("does-not-exist.hprof");
            assertThrows(IOException.class, () -> HprofMappedFile.open(missing));
        }
    }

    @Nested
    class Accessors {

        @Test
        void readsBigEndianPrimitives(@TempDir Path tmp) throws IOException {
            // Header + a payload region we'll read primitives from.
            byte[] payload = new byte[]{
                    (byte) 0xCA,                                                             // byte
                    (byte) 0x12, (byte) 0x34,                                                // short = 0x1234
                    (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF,                     // int = 0xDEADBEEF
                    (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
                    (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF                      // long
            };
            Path hprof = writeHprofWithHeaderAndPayload(tmp, HprofHeader.MAGIC_1_0_2, 8, 0L, payload);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                long base = file.header().headerSize();
                assertEquals((byte) 0xCA, file.readByte(base));
                assertEquals((short) 0x1234, file.readShort(base + 1));
                assertEquals(0xDEADBEEF, file.readInt(base + 3));
                assertEquals(0x0123456789ABCDEFL, file.readLong(base + 7));
            }
        }

        @Test
        void readIdReturnsZeroExtendedIntForIdSize4(@TempDir Path tmp) throws IOException {
            byte[] payload = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFE};
            Path hprof = writeHprofWithHeaderAndPayload(tmp, HprofHeader.MAGIC_1_0_2, 4, 0L, payload);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                long id = file.readId(file.header().headerSize());
                assertEquals(0xFFFFFFFEL, id);
            }
        }

        @Test
        void readIdReturnsLongForIdSize8(@TempDir Path tmp) throws IOException {
            byte[] payload = new byte[]{
                    (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
                    (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
            };
            Path hprof = writeHprofWithHeaderAndPayload(tmp, HprofHeader.MAGIC_1_0_2, 8, 0L, payload);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                long id = file.readId(file.header().headerSize());
                assertEquals(0x0123456789ABCDEFL, id);
            }
        }

        @Test
        void readBytesCopiesIntoFreshArray(@TempDir Path tmp) throws IOException {
            byte[] payload = "JEFFREY".getBytes(StandardCharsets.UTF_8);
            Path hprof = writeHprofWithHeaderAndPayload(tmp, HprofHeader.MAGIC_1_0_2, 8, 0L, payload);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                byte[] read = file.readBytes(file.header().headerSize(), payload.length);
                assertArrayEquals(payload, read);
            }
        }
    }

    private static Path writeHprofWithHeader(Path tmp, String magic, int idSize, long timestampMs) throws IOException {
        return writeHprofWithHeaderAndPayload(tmp, magic, idSize, timestampMs, new byte[0]);
    }

    private static Path writeHprofWithHeaderAndPayload(
            Path tmp, String magic, int idSize, long timestampMs, byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeBytes(magic);
            dos.writeByte(0);
            dos.writeInt(idSize);
            dos.writeLong(timestampMs);
            dos.write(payload);
        }
        Path hprof = tmp.resolve("synthetic.hprof");
        Files.write(hprof, baos.toByteArray());
        return hprof;
    }
}
