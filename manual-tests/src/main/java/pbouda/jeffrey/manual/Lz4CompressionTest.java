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

package pbouda.jeffrey.manual;

import pbouda.jeffrey.shared.compression.Lz4Compressor;
import pbouda.jeffrey.shared.filesystem.JeffreyDirs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Lz4CompressionTest {

    private static final Path JFRS_DIR = Path.of("manual-tests/jfrs");
    private static final JeffreyDirs JEFFREY_DIRS = new JeffreyDirs(
            null, JFRS_DIR);

    private static final Lz4Compressor LZ4_COMPRESSOR = new Lz4Compressor(JEFFREY_DIRS);

    private static final Path OUTPUT_DIR = JEFFREY_DIRS.newTempDir().path();

    static void main() throws IOException {
        // Create output directory
        Files.createDirectories(OUTPUT_DIR);

        // List all JFR files
        List<Path> jfrFiles = Files.list(JFRS_DIR)
                .filter(p -> p.toString().endsWith(".jfr"))
                .sorted()
                .toList();

        System.out.println("Found " + jfrFiles.size() + " JFR files:");
        jfrFiles.forEach(f -> System.out.println("  - " + f.getFileName()));

        // Step 1: Compress each file individually
        System.out.println("\n=== Compressing files ===");
        List<Path> compressedFiles = jfrFiles.stream()
                .map(Lz4CompressionTest::compressFile)
                .toList();

        // Step 2: Concatenate all compressed files (byte-level merge)
        Path concatenatedLz4 = OUTPUT_DIR.resolve("concatenated.jfr.lz4");
        System.out.println("\n=== Concatenating compressed files ===");
        concatenateFiles(compressedFiles, concatenatedLz4);

        // Step 3: Decompress the concatenated file
        Path decompressedJfr = OUTPUT_DIR.resolve("concatenated.jfr");
        System.out.println("\n=== Decompressing concatenated file ===");
        decompressFile(concatenatedLz4, decompressedJfr);

        // Print summary
        System.out.println("\n=== Summary ===");
        long originalTotalSize = jfrFiles.stream()
                .mapToLong(Lz4CompressionTest::fileSize)
                .sum();
        long compressedTotalSize = compressedFiles.stream()
                .mapToLong(Lz4CompressionTest::fileSize)
                .sum();
        long concatenatedSize = fileSize(concatenatedLz4);
        long decompressedSize = fileSize(decompressedJfr);

        System.out.println("Original total size:     " + formatSize(originalTotalSize));
        System.out.println("Compressed total size:   " + formatSize(compressedTotalSize));
        System.out.println("Concatenated LZ4 size:   " + formatSize(concatenatedSize));
        System.out.println("Decompressed size:       " + formatSize(decompressedSize));
        System.out.println("Compression ratio:       " + String.format("%.2fx", (double) originalTotalSize / concatenatedSize));

        // Verify: decompressed size should equal original total size
        if (decompressedSize == originalTotalSize) {
            System.out.println("\n✓ SUCCESS: Decompressed file size matches original total size!");
        } else {
            System.out.println("\n✗ MISMATCH: Decompressed size (" + decompressedSize +
                    ") != original total (" + originalTotalSize + ")");
        }
    }

    private static Path compressFile(Path source) {
        try {
            Path target = OUTPUT_DIR.resolve(source.getFileName() + ".lz4");
            long startTime = System.nanoTime();
            Lz4Compressor.compress(source, target);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            long originalSize = Files.size(source);
            long compressedSize = Files.size(target);
            double ratio = (double) originalSize / compressedSize;

            System.out.printf("  %s: %s -> %s (%.2fx) in %d ms%n",
                    source.getFileName(),
                    formatSize(originalSize),
                    formatSize(compressedSize),
                    ratio,
                    duration);

            return target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress: " + source, e);
        }
    }

    private static void concatenateFiles(List<Path> files, Path output) throws IOException {
        long startTime = System.nanoTime();

        try (OutputStream out = Files.newOutputStream(output)) {
            for (Path file : files) {
                System.out.println("  Appending: " + file.getFileName());
                Files.copy(file, out);
            }
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("  Concatenation completed in " + duration + " ms");
        System.out.println("  Output: " + output + " (" + formatSize(fileSize(output)) + ")");
    }

    private static void decompressFile(Path source, Path target) throws IOException {
        long startTime = System.nanoTime();

        LZ4_COMPRESSOR.decompressToDir(source, target.getParent());

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("  Decompression completed in " + duration + " ms");
        System.out.println("  Output: " + target + " (" + formatSize(fileSize(target)) + ")");
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
