/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public abstract class FileSystemUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemUtils.class);
    private static final Comparator<Path> DEFAULT_FILE_COMPARATOR =
            Comparator.comparing(FileSystemUtils::modifiedAt).reversed();

    public static String filenameWithoutExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return fileName;
        } else {
            return fileName.substring(0, dotIndex);
        }
    }

    public static boolean isNotHidden(Path path) {
        try {
            return !Files.isHidden(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot recognize whether the file is hidden or not", e);
        }
    }

    public static boolean isDirectory(Path path) {
        return Files.exists(path) && Files.isDirectory(path);
    }

    public static boolean isFile(Path path) {
        return Files.exists(path) && Files.isRegularFile(path);
    }

    public static Instant modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read the last modification time: " + path, e);
        }
    }

    public static Optional<Instant> directoryModification(Path directory) {
        return sortedFilesInDirectory(directory, DEFAULT_FILE_COMPARATOR).stream()
                .filter(file -> Files.isRegularFile(file) && FileSystemUtils.isNotHidden(file))
                .findFirst()
                .map(FileSystemUtils::modifiedAt);
    }

    public static List<Path> sortedFilesInDirectory(Path directory, Comparator<Path> comparator) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.sorted(comparator).toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read directory: " + directory, e);
        }
    }

    public static Instant createdAt(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime fileTime = attr.creationTime();
            return fileTime.toInstant();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read the last creation time: " + path, e);
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

    public static List<Path> allFilesInDirectory(Path dir) {
        try (var stream = Files.walk(dir, 1)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(FileSystemUtils::isNotHidden)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("", e);
        }
    }

    public static List<Path> allDirectoriesInDirectory(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(FileSystemUtils::isDirectory)
                    .filter(FileSystemUtils::isNotHidden)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Cannot resolve directories in a directory: " + dir, e);
        }
    }

    public static Optional<Path> findSupportedFileInDir(Path dir, SupportedRecordingFile recordingFileType) {
        BiPredicate<Path, BasicFileAttributes> matcher = (path, _) -> recordingFileType.matches(path.getFileName());
        try (var stream = Files.find(dir, 1, matcher)) {
            return stream.findFirst();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Searching in directory failed: directory=" + dir + " supported_file_type=" + recordingFileType, e);
        }
    }

    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot get size of file: " + path, e);
        }
    }

    public static void removeDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Cannot complete removing of a directory: " + directory, e);
        }
    }

    public static void removeFile(Path targetPath) {
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            LOG.error("Failed to delete the recording: {}", targetPath, e);
        }
    }

    public static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot delete file: " + path, e);
        }
    }
}
