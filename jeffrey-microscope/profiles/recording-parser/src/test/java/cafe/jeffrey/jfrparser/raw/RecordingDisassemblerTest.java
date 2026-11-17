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
import java.util.ArrayList;
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

        /**
         * The callback is what lets the caller parse chunk N while chunk N+1 is still being copied,
         * so each chunk has to be announced in order and, crucially, be complete on disk by the time
         * it is — a reader handed a half-written chunk would fail or silently read short.
         */
        @Test
        void announcesEachChunkCompleteAndInOrder() throws IOException {
            byte[] firstChunk = syntheticChunk(FIRST_CHUNK_PAYLOAD_SIZE, (byte) 0x0A);
            byte[] secondChunk = syntheticChunk(SECOND_CHUNK_PAYLOAD_SIZE, (byte) 0x0B);
            Path recording = writeRecording("recording.jfr", firstChunk, secondChunk);

            List<byte[]> announced = new ArrayList<>();
            List<Path> chunks = RecordingDisassembler.disassemble(
                    recording,
                    tempDir.resolve("chunks"),
                    chunk -> announced.add(readQuietly(chunk)));

            assertEquals(chunks.size(), announced.size());
            assertArrayEquals(firstChunk, announced.get(0));
            assertArrayEquals(secondChunk, announced.get(1));
        }

        private static byte[] readQuietly(Path chunk) {
            try {
                return Files.readAllBytes(chunk);
            } catch (IOException e) {
                throw new IllegalStateException("Chunk was not readable when announced: " + chunk, e);
            }
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
