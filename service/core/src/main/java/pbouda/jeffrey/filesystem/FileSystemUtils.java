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

package pbouda.jeffrey.filesystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public abstract class FileSystemUtils {

    public static void createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create parent directories: " + path);
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

    public static Path upload(Path targetPath, InputStream stream) {
        try (var output = Files.newOutputStream(targetPath)) {
            stream.transferTo(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return targetPath;
    }

    public static void concatFiles(List<Path> sources, Path target) {
        try (var output = Files.newOutputStream(target)) {
            for (Path source : sources) {
                Files.copy(source, output);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot concatenate files", e);
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
