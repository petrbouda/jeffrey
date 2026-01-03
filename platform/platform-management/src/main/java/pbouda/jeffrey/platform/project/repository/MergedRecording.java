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

package pbouda.jeffrey.platform.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AutoCloseable wrapper for a merged recording file.
 * <p>
 * When closed, the temporary merged file is automatically deleted.
 * Use with try-with-resources to ensure cleanup.
 * </p>
 *
 * <pre>{@code
 * try (MergedRecording merged = repositoryStorage.mergeRecordings(sessionId)) {
 *     // Use merged.path() to access the file
 * } // File is automatically deleted here
 * }</pre>
 */
public class MergedRecording implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MergedRecording.class);

    private final Path path;
    private final long size;

    public MergedRecording(Path path) {
        this.path = path;
        try {
            this.size = Files.size(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get size of merged recording: " + path, e);
        }
    }

    /**
     * @return path to the merged recording file
     */
    public Path path() {
        return path;
    }

    /**
     * @return the filename for the merged recording (e.g., "session-id.jfr.lz4")
     */
    public String filename() {
        return path.getFileName().toString();
    }

    /**
     * @return size of the merged recording file in bytes
     */
    public long size() {
        return size;
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("Failed to delete merged recording temp file: {}", path, e);
        }
    }
}
