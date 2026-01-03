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

package pbouda.jeffrey.shared.common.compression;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Lz4CompressorTest {

    @TempDir
    Path tempDir;

    @Nested
    class StaticCompressDecompress {

        @Test
        void compressAndDecompressRoundTrip() throws IOException {
            String content = "Hello, this is test content for LZ4 compression!";
            Path sourceFile = tempDir.resolve("test.txt");
            Files.writeString(sourceFile, content);

            Path compressedFile = tempDir.resolve("test.txt.lz4");
            Path decompressedFile = tempDir.resolve("test-decompressed.txt");

            Lz4Compressor.compress(sourceFile, compressedFile);
            assertTrue(Files.exists(compressedFile));
            assertTrue(Files.size(compressedFile) > 0);

            Lz4Compressor.decompress(compressedFile, decompressedFile);
            assertTrue(Files.exists(decompressedFile));

            String decompressedContent = Files.readString(decompressedFile);
            assertEquals(content, decompressedContent);
        }

        @Test
        void compressLargeFile() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("Line ").append(i).append(": This is some repeated content for testing.\n");
            }
            String content = sb.toString();

            Path sourceFile = tempDir.resolve("large.txt");
            Files.writeString(sourceFile, content);

            Path compressedFile = tempDir.resolve("large.txt.lz4");
            Path decompressedFile = tempDir.resolve("large-decompressed.txt");

            Lz4Compressor.compress(sourceFile, compressedFile);

            // LZ4 should compress repetitive content well
            assertTrue(Files.size(compressedFile) < Files.size(sourceFile));

            Lz4Compressor.decompress(compressedFile, decompressedFile);
            String decompressedContent = Files.readString(decompressedFile);
            assertEquals(content, decompressedContent);
        }

        @Test
        void compressBinaryData() throws IOException {
            byte[] binaryContent = new byte[1024];
            for (int i = 0; i < binaryContent.length; i++) {
                binaryContent[i] = (byte) (i % 256);
            }

            Path sourceFile = tempDir.resolve("binary.dat");
            Files.write(sourceFile, binaryContent);

            Path compressedFile = tempDir.resolve("binary.dat.lz4");
            Path decompressedFile = tempDir.resolve("binary-decompressed.dat");

            Lz4Compressor.compress(sourceFile, compressedFile);
            Lz4Compressor.decompress(compressedFile, decompressedFile);

            byte[] decompressedContent = Files.readAllBytes(decompressedFile);
            assertArrayEquals(binaryContent, decompressedContent);
        }

        @Test
        void compressEmptyFile() throws IOException {
            Path sourceFile = tempDir.resolve("empty.txt");
            Files.writeString(sourceFile, "");

            Path compressedFile = tempDir.resolve("empty.txt.lz4");
            Path decompressedFile = tempDir.resolve("empty-decompressed.txt");

            Lz4Compressor.compress(sourceFile, compressedFile);
            Lz4Compressor.decompress(compressedFile, decompressedFile);

            assertEquals(0, Files.size(decompressedFile));
        }
    }

    @Nested
    class DecompressStream {

        @Test
        void decompressStreamReturnsValidInputStream() throws IOException {
            String content = "Stream test content";
            Path sourceFile = tempDir.resolve("stream-test.txt");
            Files.writeString(sourceFile, content);

            Path compressedFile = tempDir.resolve("stream-test.txt.lz4");
            Lz4Compressor.compress(sourceFile, compressedFile);

            try (InputStream stream = Lz4Compressor.decompressStream(compressedFile)) {
                byte[] bytes = stream.readAllBytes();
                String result = new String(bytes, StandardCharsets.UTF_8);
                assertEquals(content, result);
            }
        }
    }

    @Nested
    class IsLz4Compressed {

        @Test
        void detectsLz4Extension() {
            assertTrue(Lz4Compressor.isLz4Compressed(Path.of("file.lz4")));
            assertTrue(Lz4Compressor.isLz4Compressed(Path.of("file.jfr.lz4")));
            assertTrue(Lz4Compressor.isLz4Compressed(Path.of("/path/to/file.lz4")));
        }

        @Test
        void rejectsNonLz4Extension() {
            assertFalse(Lz4Compressor.isLz4Compressed(Path.of("file.txt")));
            assertFalse(Lz4Compressor.isLz4Compressed(Path.of("file.jfr")));
            assertFalse(Lz4Compressor.isLz4Compressed(Path.of("file.gz")));
            assertFalse(Lz4Compressor.isLz4Compressed(Path.of("lz4")));
        }

        @Test
        void rejectsPartialMatch() {
            assertFalse(Lz4Compressor.isLz4Compressed(Path.of("file.lz4.backup")));
            assertFalse(Lz4Compressor.isLz4Compressed(Path.of("filelz4")));
        }
    }

    @Nested
    class InstanceMethods {

        @Test
        void compressToDirCreatesCompressedFileInTargetDirectory() throws IOException {
            Path homeDir = tempDir.resolve("home");
            Path jeffreyTempDir = tempDir.resolve("jeffrey-temp");
            Files.createDirectories(homeDir);
            Files.createDirectories(jeffreyTempDir);

            JeffreyDirs jeffreyDirs = new JeffreyDirs(homeDir, jeffreyTempDir);
            Lz4Compressor compressor = new Lz4Compressor(jeffreyDirs);

            String content = "Test content for instance method";
            Path sourceFile = tempDir.resolve("source.txt");
            Files.writeString(sourceFile, content);

            Path targetDir = tempDir.resolve("target");
            Files.createDirectories(targetDir);

            Path compressedFile = compressor.compressToDir(sourceFile, targetDir);

            assertTrue(Files.exists(compressedFile));
            assertEquals("source.txt.lz4", compressedFile.getFileName().toString());
            assertTrue(compressedFile.startsWith(targetDir));
        }

        @Test
        void decompressToDirRemovesLz4Extension() throws IOException {
            Path homeDir = tempDir.resolve("home");
            Path jeffreyTempDir = tempDir.resolve("jeffrey-temp");
            Files.createDirectories(homeDir);
            Files.createDirectories(jeffreyTempDir);

            JeffreyDirs jeffreyDirs = new JeffreyDirs(homeDir, jeffreyTempDir);
            Lz4Compressor compressor = new Lz4Compressor(jeffreyDirs);

            String content = "Test content";
            Path sourceFile = tempDir.resolve("test.txt");
            Files.writeString(sourceFile, content);

            Path compressedFile = tempDir.resolve("test.txt.lz4");
            Lz4Compressor.compress(sourceFile, compressedFile);

            Path targetDir = tempDir.resolve("decompressed");
            Files.createDirectories(targetDir);

            Path decompressedFile = compressor.decompressToDir(compressedFile, targetDir);

            assertEquals("test.txt", decompressedFile.getFileName().toString());
            assertEquals(content, Files.readString(decompressedFile));
        }

        @Test
        void decompressToDirAddsDecompressedSuffixIfNoLz4Extension() throws IOException {
            Path homeDir = tempDir.resolve("home");
            Path jeffreyTempDir = tempDir.resolve("jeffrey-temp");
            Files.createDirectories(homeDir);
            Files.createDirectories(jeffreyTempDir);

            JeffreyDirs jeffreyDirs = new JeffreyDirs(homeDir, jeffreyTempDir);
            Lz4Compressor compressor = new Lz4Compressor(jeffreyDirs);

            String content = "Test content";
            Path sourceFile = tempDir.resolve("test.txt");
            Files.writeString(sourceFile, content);

            // Compress but rename without .lz4 extension
            Path compressedFile = tempDir.resolve("test.compressed");
            Lz4Compressor.compress(sourceFile, compressedFile);

            Path targetDir = tempDir.resolve("decompressed");
            Files.createDirectories(targetDir);

            Path decompressedFile = compressor.decompressToDir(compressedFile, targetDir);

            assertEquals("test.compressed.decompressed", decompressedFile.getFileName().toString());
            assertEquals(content, Files.readString(decompressedFile));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void compressNonExistentFileThrowsException() {
            Path nonExistent = tempDir.resolve("does-not-exist.txt");
            Path target = tempDir.resolve("target.lz4");

            assertThrows(RuntimeException.class, () -> Lz4Compressor.compress(nonExistent, target));
        }

        @Test
        void decompressInvalidLz4FileThrowsException() throws IOException {
            Path invalidFile = tempDir.resolve("invalid.lz4");
            Files.writeString(invalidFile, "This is not valid LZ4 data");
            Path target = tempDir.resolve("target.txt");

            assertThrows(RuntimeException.class, () -> Lz4Compressor.decompress(invalidFile, target));
        }
    }

    @Nested
    class RealJfrFiles {

        private static final Path JFRS_DIR = Path.of("src/test/resources/jfrs");

        @Test
        void compressAndDecompressProfile1RoundTrip() throws IOException {
            Path jfrFile = JFRS_DIR.resolve("profile-1.jfr");
            verifyRoundTrip(jfrFile);
        }

        @Test
        void compressionReducesFileSizeForAllJfrFiles() throws IOException {
            try (var stream = Files.list(JFRS_DIR)) {
                stream.filter(p -> p.toString().endsWith(".jfr")).forEach(jfrFile -> {
                    try {
                        long originalSize = Files.size(jfrFile);
                        String fileName = jfrFile.getFileName().toString();

                        Path compressedFile = tempDir.resolve(fileName + ".lz4");
                        Lz4Compressor.compress(jfrFile, compressedFile);

                        long compressedSize = Files.size(compressedFile);
                        assertTrue(compressedSize > 0, "Compressed file should not be empty: " + fileName);
                        assertTrue(compressedSize < originalSize, "Compressed size should be smaller than original for: " + fileName + " (original=" + originalSize + ", compressed=" + compressedSize + ")");
                    } catch (IOException e) {
                        throw new RuntimeException("Failed for file: " + jfrFile, e);
                    }
                });
            }
        }

        @Test
        void decompressStreamWorksWithAllJfrFiles() throws IOException {
            try (var stream = Files.list(JFRS_DIR)) {
                stream.filter(p -> p.toString().endsWith(".jfr")).forEach(jfrFile -> {
                    try {
                        byte[] originalContent = Files.readAllBytes(jfrFile);
                        String fileName = jfrFile.getFileName().toString();

                        Path compressedFile = tempDir.resolve(fileName + ".lz4");
                        Lz4Compressor.compress(jfrFile, compressedFile);

                        try (InputStream decompressStream = Lz4Compressor.decompressStream(compressedFile)) {
                            byte[] decompressedContent = decompressStream.readAllBytes();
                            assertArrayEquals(originalContent, decompressedContent, "Decompression stream failed for: " + fileName);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed for file: " + jfrFile, e);
                    }
                });
            }
        }

        @Test
        void allJfrFilesRoundTripSuccessfully() throws IOException {
            try (var stream = Files.list(JFRS_DIR)) {
                stream.filter(p -> p.toString().endsWith(".jfr")).forEach(jfrFile -> {
                    try {
                        verifyRoundTrip(jfrFile);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed for file: " + jfrFile, e);
                    }
                });
            }
        }

        private void verifyRoundTrip(Path jfrFile) throws IOException {
            byte[] originalContent = Files.readAllBytes(jfrFile);
            String fileName = jfrFile.getFileName().toString();

            Path compressedFile = tempDir.resolve(fileName + ".lz4");
            Path decompressedFile = tempDir.resolve(fileName + ".decompressed");

            Lz4Compressor.compress(jfrFile, compressedFile);
            assertTrue(Files.exists(compressedFile), "Compressed file should exist for: " + fileName);
            assertTrue(Files.size(compressedFile) > 0, "Compressed file should not be empty for: " + fileName);

            Lz4Compressor.decompress(compressedFile, decompressedFile);
            assertTrue(Files.exists(decompressedFile), "Decompressed file should exist for: " + fileName);

            byte[] decompressedContent = Files.readAllBytes(decompressedFile);
            assertArrayEquals(originalContent, decompressedContent, "Round trip failed for: " + fileName);
        }
    }
}
