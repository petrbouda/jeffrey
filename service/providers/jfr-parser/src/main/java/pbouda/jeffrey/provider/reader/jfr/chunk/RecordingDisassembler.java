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

package pbouda.jeffrey.provider.reader.jfr.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.compression.Lz4Compressor;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Disassembles JFR recordings into separate chunk files.
 * Supports both regular .jfr files (using zero-copy FileChannel transfer)
 * and LZ4 compressed .jfr.lz4 files (using streaming decompression).
 */
public abstract class RecordingDisassembler implements JfrChunkConstants {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingDisassembler.class);

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB

    /**
     * Disassembles a JFR recording (plain or LZ4 compressed) into separate chunk files.
     *
     * @param recording Path to .jfr or .jfr.lz4 file
     * @param outputDir Directory to write chunk files (must exist)
     * @return List of paths to created chunk files
     */
    public static List<Path> disassemble(Path recording, Path outputDir) {
        LOG.debug("Disassembling recording: {} to {}", recording, outputDir);

        if (Lz4Compressor.isLz4Compressed(recording)) {
            try (InputStream input = Lz4Compressor.decompressStream(recording)) {
                return disassembleStream(input, outputDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to disassemble LZ4 recording: " + recording, e);
            }
        } else {
            return disassembleFile(recording, outputDir);
        }
    }

    /**
     * Disassembles a JFR recording from an InputStream.
     * Useful for decompressed LZ4 streams or other input sources.
     *
     * @param input     InputStream containing JFR data
     * @param outputDir Directory to write chunk files
     * @return List of paths to created chunk files
     */
    public static List<Path> disassembleStream(InputStream input, Path outputDir) {
        List<Path> chunkFiles = new ArrayList<>();
        byte[] headerBytes = new byte[CHUNK_HEADER_SIZE];
        byte[] copyBuffer = new byte[BUFFER_SIZE];
        int chunkIndex = 0;

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + outputDir, e);
        }

        try {
            while (true) {
                // Read header
                int headerRead = StreamUtils.readFully(input, headerBytes, 0, CHUNK_HEADER_SIZE);
                if (headerRead == 0) {
                    break; // Clean EOF at chunk boundary
                }
                if (headerRead < CHUNK_HEADER_SIZE) {
                    LOG.warn("Incomplete chunk header at chunk {}: read {} bytes", chunkIndex, headerRead);
                    break;
                }

                // Parse chunk size from header
                long chunkSize = parseChunkSize(headerBytes);
                long remainingBytes = chunkSize - CHUNK_HEADER_SIZE;

                LOG.trace("Processing chunk {} with size {} bytes", chunkIndex, chunkSize);

                // Write to output file
                Path outputPath = outputDir.resolve("chunk_" + chunkIndex + ".jfr");
                try (OutputStream output = Files.newOutputStream(outputPath, CREATE, WRITE)) {
                    // Write header first
                    output.write(headerBytes);

                    // Copy remaining chunk data
                    while (remainingBytes > 0) {
                        int toRead = (int) Math.min(copyBuffer.length, remainingBytes);
                        int read = input.read(copyBuffer, 0, toRead);
                        if (read < 0) {
                            throw new EOFException("Unexpected EOF at chunk " + chunkIndex +
                                    ", remaining bytes: " + remainingBytes);
                        }
                        output.write(copyBuffer, 0, read);
                        remainingBytes -= read;
                    }
                }

                chunkFiles.add(outputPath);
                chunkIndex++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to disassemble recording stream at chunk " + chunkIndex, e);
        }

        LOG.debug("Disassembled {} chunks from stream", chunkFiles.size());
        return chunkFiles;
    }

    /**
     * File-based disassembly using FileChannel.transferTo() for efficient zero-copy transfer.
     *
     * @param recording Path to JFR file
     * @param outputDir Directory to write chunk files
     * @return List of paths to created chunk files
     */
    private static List<Path> disassembleFile(Path recording, Path outputDir) {
        List<Path> chunkFiles = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + outputDir, e);
        }

        try (FileChannel channel = FileChannel.open(recording)) {
            long recordingSize = Files.size(recording);
            int chunkIndex = 0;

            while (channel.position() + CHUNK_HEADER_SIZE <= recordingSize) {
                long chunkStart = channel.position();

                // Read header
                int read = channel.read(buffer);
                if (read < CHUNK_HEADER_SIZE) {
                    LOG.warn("Incomplete chunk header at position {}", chunkStart);
                    break;
                }

                buffer.flip();
                long chunkSize = parseChunkSize(buffer);
                buffer.clear();

                LOG.trace("Processing chunk {} at position {} with size {} bytes",
                        chunkIndex, chunkStart, chunkSize);

                // Write chunk to output file using zero-copy transfer
                Path outputPath = outputDir.resolve("chunk_" + chunkIndex + ".jfr");
                try (FileChannel output = FileChannel.open(outputPath, CREATE, WRITE)) {
                    long transferred = 0;
                    while (transferred < chunkSize) {
                        long n = channel.transferTo(chunkStart + transferred,
                                chunkSize - transferred, output);
                        if (n <= 0) {
                            throw new IOException("transferTo returned " + n);
                        }
                        transferred += n;
                    }
                }

                chunkFiles.add(outputPath);

                // Move to next chunk
                channel.position(chunkStart + chunkSize);
                chunkIndex++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to disassemble recording: " + recording, e);
        }

        LOG.debug("Disassembled {} chunks from file: {}", chunkFiles.size(), recording);
        return chunkFiles;
    }

    /**
     * Parses chunk size from header bytes. Only validates magic number.
     */
    private static long parseChunkSize(byte[] headerBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return parseChunkSize(buffer);
    }

    /**
     * Parses chunk size from header buffer. Only validates magic number.
     */
    private static long parseChunkSize(ByteBuffer buffer) {
        int magic = buffer.getInt();      // bytes 0-3
        buffer.getInt();                   // version bytes 4-7 (skip)
        long size = buffer.getLong();     // bytes 8-15

        if (magic != CHUNK_MAGIC) {
            throw new RuntimeException("Invalid JFR chunk magic: " + Integer.toHexString(magic) +
                    ", expected: " + Integer.toHexString(CHUNK_MAGIC));
        }

        return size;
    }
}
