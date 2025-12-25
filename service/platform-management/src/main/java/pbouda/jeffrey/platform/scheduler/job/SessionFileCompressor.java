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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility class for compressing JFR files in recording sessions.
 * Used by both the periodic scheduler job and for immediate on-demand compression.
 */
public class SessionFileCompressor {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFileCompressor.class);

    private final Lz4Compressor lz4Compressor;

    public SessionFileCompressor(Lz4Compressor lz4Compressor) {
        this.lz4Compressor = lz4Compressor;
    }

    /**
     * Compresses all eligible JFR files in the given session.
     * Only compresses files that are:
     * - Uncompressed JFR files (not JFR_LZ4)
     * - Finished (not actively being written)
     * - Not matching ASPROF_TEMP pattern
     *
     * @param session     the recording session to compress
     * @param projectName project name for logging
     */
    public void compressSession(RecordingSession session, String projectName, SupportedRecordingFile type) {
        List<RepositoryFile> compressibleFiles = session.files().stream()
                // Only uncompressed JFR files (not JFR_LZ4 - already compressed)
                .filter(file -> file.fileType() == type)
                // Only finished files are safe to compress
                .filter(RepositoryFile::isFinished)
                .toList();

        if (compressibleFiles.isEmpty()) {
            LOG.debug("No files to compress in session: project='{}' session='{}'",
                    projectName, session.id());
        }

        for (RepositoryFile file : compressibleFiles) {
            if (!compressFile(projectName, session.id(), file)) {
                break;
            }
        }
    }

    /**
     * Compresses a single file using LZ4 compression.
     *
     * @param projectName project name for logging
     * @param sessionId   session ID for logging
     * @param file        the file to compress
     */
    private boolean compressFile(String projectName, String sessionId, RepositoryFile file) {
        Path sourcePath = file.filePath();

        if (!FileSystemUtils.isFile(sourcePath)) {
            LOG.warn("Source file does not exist, skipping compression: {}", sourcePath);
            return false;
        }

        try {
            // Compress to .jfr.lz4
            Path compressedPath = lz4Compressor.compressAndMove(sourcePath);

            // Verify compression was successful before deleting original
            if (Files.exists(compressedPath) && Files.size(compressedPath) > 0) {
                Files.deleteIfExists(sourcePath);
                return true;
            } else {
                LOG.error("Compression verification failed, keeping original: {}", sourcePath);
                // Clean up failed compression attempt
                Files.deleteIfExists(compressedPath);
                return false;
            }
        } catch (IOException e) {
            LOG.error("Failed to compress file: project='{}' session='{}' file='{}' error='{}'",
                    projectName, sessionId, file.name(), e.getMessage());
            return false;
        }
    }
}
