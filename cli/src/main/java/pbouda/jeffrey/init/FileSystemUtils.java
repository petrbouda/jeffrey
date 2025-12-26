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

package pbouda.jeffrey.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class FileSystemUtils {

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

    public static boolean isNotHidden(Path path) {
        try {
            return !Files.isHidden(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot recognize whether the file is hidden or not", e);
        }
    }
}
