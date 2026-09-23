/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.compression;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Utility class for LZ4 compression and decompression of files.
 */
public class Lz4Compressor {

    private static final String LZ4_EXTENSION = ".lz4";

    private final TempDirFactory tempDirFactory;

    public Lz4Compressor(TempDirFactory tempDirFactory) {
        this.tempDirFactory = tempDirFactory;
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
     * Checks if a file is LZ4 compressed based on its extension.
     *
     * @param path the file path to check
     * @return true if the file has a .lz4 extension
     */
    public static boolean isLz4Compressed(Path path) {
        return path.toString().endsWith(LZ4_EXTENSION);
    }
}
