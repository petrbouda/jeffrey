/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.microscope.common.filesystem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

public abstract class FileSystemUtils {

    public static boolean isDirectory(Path path) {
        return Files.exists(path) && Files.isDirectory(path);
    }

    public static boolean isFile(Path path) {
        return Files.exists(path) && Files.isRegularFile(path);
    }

    /**
     * One stat of the file — size, kind, timestamps — for a caller that will ask several of
     * those questions; each {@code Files.size}/{@code isRegularFile}/{@code createdAt} is a stat
     * of its own, and on a network mount every one is a round trip.
     */
    public static BasicFileAttributes readAttributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the attributes of the file: " + path, e);
        }
    }

    public static Instant createdAt(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime fileTime = attr.creationTime();
            return fileTime.toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the last creation time: " + path, e);
        }
    }

    public static Path createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                return Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create parent directories: " + path);
        }
        return path;
    }

    public static Path removeAndCreateDirectories(Path path) {
        try {
            removeDirectory(path);
            return Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create parent directories: " + path);
        }
    }

    /**
     * @throws UncheckedIOException when the file cannot be read, wrapping the {@link IOException}
     */
    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot get size of file: " + path, e);
        }
    }

    public static void removeDirectory(Path directory) {
        if (!FileSystemUtils.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(FileSystemUtils::delete);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot complete removing of a directory: " + directory, e);
        }
    }

    public static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete file: " + path, e);
        }
    }

    /**
     * Loads a String file from the path. It can also read from classpath resources
     * if the path starts with "classpath:", or file if the path starts with "file:".
     *
     * @param path the path to the file
     * @return the file content as string
     */
    public static String readString(String path) {
        String content;
        if (path.startsWith("classpath:")) {
            content = readFromClasspath(path);
        } else if (path.startsWith("file:")) {
            content = readFromFile(path);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported path, must start with 'classpath:' or 'file:': path=" + path);
        }

        return content;
    }

    private static String readFromFile(String path) {
        Path contentPath = Path.of(path.substring("file:".length()));
        try {
            return Files.readString(contentPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading file: " + path, e);
        }
    }

    private static String readFromClasspath(String pathOnClasspath) {
        String path = pathOnClasspath.substring("classpath:".length());
        try (InputStream stream = FileSystemUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error reading file from classpath: " + path, e);
        }
    }

}
