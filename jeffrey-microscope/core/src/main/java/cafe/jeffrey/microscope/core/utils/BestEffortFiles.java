/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.utils;

import cafe.jeffrey.microscope.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * File operations that never fail their caller: a file that cannot be read or deleted is logged and
 * passed over.
 */
public final class BestEffortFiles {

    private static final Logger LOG = LoggerFactory.getLogger(BestEffortFiles.class);

    private BestEffortFiles() {
    }

    /**
     * Calculates the total size of all files in a directory recursively.
     *
     * @param directory the directory to calculate size for
     * @return total size in bytes, or 0 if directory doesn't exist or is empty
     */
    public static long directorySize(Path directory) {
        if (!FileSystemUtils.isDirectory(directory)) {
            return 0L;
        }

        try (Stream<Path> files = Files.walk(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            LOG.warn("Cannot read file size: path={}", path);
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            LOG.warn("Cannot calculate directory size: directory={}", directory);
            return 0L;
        }
    }

    public static void removeFile(Path targetPath) {
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            LOG.error("Cannot delete file: path={}", targetPath, e);
        }
    }
}
