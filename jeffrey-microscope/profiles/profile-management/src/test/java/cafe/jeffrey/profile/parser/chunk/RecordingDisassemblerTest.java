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

package cafe.jeffrey.profile.parser.chunk;

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RecordingDisassembler")
class RecordingDisassemblerTest implements JfrChunkConstants {

    private static final int FIRST_CHUNK_PAYLOAD_SIZE = 100;
    private static final int SECOND_CHUNK_PAYLOAD_SIZE = 250;

    @TempDir
    Path tempDir;

    @Nested
    class PlainRecording {

        @Test
        void splitsRecordingIntoChunkFiles() throws IOException {
            byte[] firstChunk = syntheticChunk(FIRST_CHUNK_PAYLOAD_SIZE, (byte) 0x0A);
            byte[] secondChunk = syntheticChunk(SECOND_CHUNK_PAYLOAD_SIZE, (byte) 0x0B);
            Path recording = writeRecording("recording.jfr", firstChunk, secondChunk);

            List<Path> chunks = RecordingDisassembler.disassemble(recording, tempDir.resolve("chunks"));

            assertEquals(2, chunks.size());
            assertArrayEquals(firstChunk, Files.readAllBytes(chunks.get(0)));
            assertArrayEquals(secondChunk, Files.readAllBytes(chunks.get(1)));
        }
    }

    @Nested
    class Lz4CompressedRecording {

        @Test
        void streamsSingleFrameLz4DirectlyIntoChunkFiles() throws IOException {
            byte[] firstChunk = syntheticChunk(FIRST_CHUNK_PAYLOAD_SIZE, (byte) 0x1A);
            byte[] secondChunk = syntheticChunk(SECOND_CHUNK_PAYLOAD_SIZE, (byte) 0x1B);
            Path plain = writeRecording("recording.jfr", firstChunk, secondChunk);

            Path compressed = tempDir.resolve("recording.jfr.lz4");
            Lz4Compressor.compress(plain, compressed);

            List<Path> chunks = RecordingDisassembler.disassemble(compressed, tempDir.resolve("chunks"));

            assertEquals(2, chunks.size());
            assertArrayEquals(firstChunk, Files.readAllBytes(chunks.get(0)));
            assertArrayEquals(secondChunk, Files.readAllBytes(chunks.get(1)));
        }

        /**
         * LZ4 frame format allows concatenating independently compressed frames into a single
         * file (this is how merged recordings are produced). The streaming decompressor must
         * read across the frame boundary — lz4-java's LZ4FrameInputStream does this by default
         * (decompressConcatenated behavior), which this test pins down.
         */
        @Test
        void streamsMultiFrameLz4AcrossFrameBoundaries() throws IOException {
            byte[] firstChunk = syntheticChunk(FIRST_CHUNK_PAYLOAD_SIZE, (byte) 0x2A);
            byte[] secondChunk = syntheticChunk(SECOND_CHUNK_PAYLOAD_SIZE, (byte) 0x2B);

            Path firstPlain = writeRecording("first.jfr", firstChunk);
            Path secondPlain = writeRecording("second.jfr", secondChunk);

            Path firstFrame = Lz4Compressor.compress(firstPlain, tempDir.resolve("first.lz4"));
            Path secondFrame = Lz4Compressor.compress(secondPlain, tempDir.resolve("second.lz4"));

            // Concatenated LZ4 frames == multi-frame file
            Path multiFrame = tempDir.resolve("recording.jfr.lz4");
            Files.write(multiFrame, Files.readAllBytes(firstFrame), StandardOpenOption.CREATE);
            Files.write(multiFrame, Files.readAllBytes(secondFrame), StandardOpenOption.APPEND);

            List<Path> chunks = RecordingDisassembler.disassemble(multiFrame, tempDir.resolve("chunks"));

            assertEquals(2, chunks.size());
            assertArrayEquals(firstChunk, Files.readAllBytes(chunks.get(0)));
            assertArrayEquals(secondChunk, Files.readAllBytes(chunks.get(1)));
        }
    }

    private Path writeRecording(String filename, byte[]... chunks) throws IOException {
        Path recording = tempDir.resolve(filename);
        for (byte[] chunk : chunks) {
            Files.write(recording, chunk, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        return recording;
    }

    /**
     * Builds a synthetic JFR chunk: a valid 68-byte header (magic + version + size)
     * followed by {@code payloadSize} bytes filled with {@code payloadFiller}.
     */
    private static byte[] syntheticChunk(int payloadSize, byte payloadFiller) {
        int totalSize = CHUNK_HEADER_SIZE + payloadSize;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(CHUNK_MAGIC);
        buffer.putInt(0x00020001);
        buffer.putLong(totalSize);
        while (buffer.position() < CHUNK_HEADER_SIZE) {
            buffer.put((byte) 0);
        }
        for (int i = 0; i < payloadSize; i++) {
            buffer.put(payloadFiller);
        }
        return buffer.array();
    }
}
