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

package cafe.jeffrey.profile.manager.heapdump;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeapDumpDecompressorTest {

    private static final byte[] DUMP_CONTENT = "JAVA PROFILE 1.0.2 synthetic-bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private Path writeGzippedDump(String fileName) throws IOException {
        Path gzPath = tempDir.resolve(fileName);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzPath))) {
            out.write(DUMP_CONTENT);
        }
        return gzPath;
    }

    @Nested
    class PathResolution {

        @Test
        void stripsGzExtensionForGzippedDump() {
            Path gzPath = tempDir.resolve("heap-dump.hprof.gz");
            assertEquals(tempDir.resolve("heap-dump.hprof"), HeapDumpDecompressor.analyzablePath(gzPath));
        }

        @Test
        void keepsPlainHprofPathUntouched() {
            Path hprofPath = tempDir.resolve("heap-dump.hprof");
            assertEquals(hprofPath, HeapDumpDecompressor.analyzablePath(hprofPath));
        }

        @Test
        void detectsGzippedSuffixCaseInsensitively() {
            assertTrue(HeapDumpDecompressor.isGzipped(tempDir.resolve("DUMP.HPROF.GZ")));
            assertFalse(HeapDumpDecompressor.isGzipped(tempDir.resolve("dump.hprof")));
        }
    }

    @Nested
    class Decompression {

        @Test
        void decompressesGzippedDumpToSibling() throws IOException {
            Path gzPath = writeGzippedDump("heap-dump.hprof.gz");

            Path result = HeapDumpDecompressor.ensureDecompressed(gzPath);

            assertEquals(tempDir.resolve("heap-dump.hprof"), result);
            assertArrayEquals(DUMP_CONTENT, Files.readAllBytes(result));
        }

        @Test
        void returnsPlainHprofWithoutTouchingFilesystem() throws IOException {
            Path hprofPath = tempDir.resolve("heap-dump.hprof");
            Files.write(hprofPath, DUMP_CONTENT);

            Path result = HeapDumpDecompressor.ensureDecompressed(hprofPath);

            assertEquals(hprofPath, result);
            try (var files = Files.list(tempDir)) {
                assertEquals(1, files.count());
            }
        }

        @Test
        void reusesUpToDateSibling() throws IOException {
            Path gzPath = writeGzippedDump("heap-dump.hprof.gz");
            Path sibling = tempDir.resolve("heap-dump.hprof");
            byte[] sentinel = "already-decompressed".getBytes(StandardCharsets.UTF_8);
            Files.write(sibling, sentinel);
            Files.setLastModifiedTime(sibling, FileTime.from(
                    Files.getLastModifiedTime(gzPath).toInstant().plusSeconds(60)));

            Path result = HeapDumpDecompressor.ensureDecompressed(gzPath);

            assertEquals(sibling, result);
            assertArrayEquals(sentinel, Files.readAllBytes(sibling));
        }

        @Test
        void reDecompressesWhenSiblingIsStale() throws IOException {
            Path gzPath = writeGzippedDump("heap-dump.hprof.gz");
            Path sibling = tempDir.resolve("heap-dump.hprof");
            Files.write(sibling, "stale-content".getBytes(StandardCharsets.UTF_8));
            Files.setLastModifiedTime(sibling, FileTime.from(Instant.EPOCH));

            Path result = HeapDumpDecompressor.ensureDecompressed(gzPath);

            assertArrayEquals(DUMP_CONTENT, Files.readAllBytes(result));
        }

        @Test
        void leavesNoTempFilesBehind() throws IOException {
            Path gzPath = writeGzippedDump("heap-dump.hprof.gz");

            HeapDumpDecompressor.ensureDecompressed(gzPath);

            try (var files = Files.list(tempDir)) {
                assertEquals(2, files.count());
            }
        }
    }
}
