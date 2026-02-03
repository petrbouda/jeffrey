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

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.shared.common.model.repository.FileExtensions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Utility class for LZ4 compression and decompression of files.
 */
public class Lz4Compressor {

    private static final String LZ4_EXTENSION = "." + FileExtensions.LZ4;

    private final JeffreyDirs jeffreyDirs;

    public Lz4Compressor(JeffreyDirs jeffreyDirs) {
        this.jeffreyDirs = jeffreyDirs;
    }

    /**
     * Compresses a file to LZ4 format.
     * The compressed file will have the same name with ".lz4" suffix appended.
     *
     * @param source the source file to compress
     * @return the path to the compressed file (.lz4 suffix appended)
     * @throws IOException if compression fails
     */
    public Path compressAndMove(Path source) throws IOException {
        String targetFilename = source.toString() + LZ4_EXTENSION;
        try (Directory tempDir = jeffreyDirs.newTempDir()) {
            Path tempFile = compressToDir(source, tempDir.path());
            compress(source, tempFile);
            return Files.move(tempFile, Path.of(targetFilename), ATOMIC_MOVE, REPLACE_EXISTING);
        }
    }

    /**
     * Compresses a file to LZ4 format in a specified target directory.
     * The compressed file will have the same name with ".lz4" suffix appended.
     *
     * @param source    the source file to compress
     * @param targetDir the target directory for the compressed output
     * @return the path to the compressed file in the target directory
     */
    public Path compressToDir(Path source, Path targetDir) {
        String targetFilename = source.getFileName() + LZ4_EXTENSION;
        Path targetPath = targetDir.resolve(targetFilename);
        return compress(source, targetPath);
    }

    /**
     * Decompresses an LZ4 compressed file to a specified target directory.
     * The decompressed file will have the ".lz4" suffix removed, or ".decompressed" appended if not present.
     *
     * @param source    the source LZ4 compressed file
     * @param targetDir the target directory for the decompressed output
     * @return the path to the decompressed file in the target directory
     */
    public Path decompressToDir(Path source, Path targetDir) {
        Path targetPath;
        String currFilename = source.getFileName().toString();
        if (currFilename.endsWith(LZ4_EXTENSION)) {
            targetPath = targetDir.resolve(currFilename.substring(0, currFilename.length() - LZ4_EXTENSION.length()));
        } else {
            targetPath = targetDir.resolve(currFilename + ".decompressed");
        }

        decompress(source, targetPath);
        return targetPath;
    }

    /**
     * Compresses a file to LZ4 format with a specific target path.
     *
     * @param source the source file to compress
     * @param target the target file path for the compressed output
     */
    public static Path compress(Path source, Path target) {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = new LZ4FrameOutputStream(Files.newOutputStream(target))) {
            in.transferTo(out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress file: source=" + source + " target=" + target, e);
        }

        return target;
    }

    /**
     * Decompresses an LZ4 compressed file to a specific target path.
     *
     * @param source the source LZ4 compressed file
     * @param target the target file path for the decompressed output
     */
    public static void decompress(Path source, Path target) {
        try (InputStream in = decompressStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress file: source=" + source + " target=" + target, e);
        }
    }

    /**
     * Creates an InputStream that decompresses data from an LZ4 compressed file.
     *
     * @param path the path to the LZ4 compressed file
     * @return an InputStream that provides decompressed data
     */
    public static InputStream decompressStream(Path path) {
        try {
            return new LZ4FrameInputStream(Files.newInputStream(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create decompress stream: file=" + path, e);
        }
    }

    /**
     * Decompresses an LZ4 compressed file and writes the decompressed data to an OutputStream.
     * Note: This method does NOT close the OutputStream - caller is responsible for closing it.
     *
     * @param source the path to the LZ4 compressed file
     * @param output the OutputStream to write decompressed data to
     */
    public static void decompressTo(Path source, OutputStream output) {
        try (InputStream in = decompressStream(source)) {
            in.transferTo(output);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress file to stream: source=" + source, e);
        }
    }

    /**
     * Checks if a file is LZ4 compressed based on its extension.
     *
     * @param path the file path to check
     * @return true if the file has a .lz4 extension
     */
    public static boolean isLz4Compressed(Path path) {
        return path.toString().endsWith(LZ4_EXTENSION);
    }
}
