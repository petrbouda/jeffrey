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

import pbouda.jeffrey.shared.model.ProjectInfo;
import pbouda.jeffrey.shared.model.RepositoryInfo;
import pbouda.jeffrey.shared.model.RepositoryType;
import pbouda.jeffrey.shared.model.repository.RecordingSession;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryStorage> {
    }

    /**
     * Information about the repository.
     *
     * @return information about the repository.
     */
    RepositoryInfo repositoryInfo();

    /**
     * Lists of files for the given session.
     *
     * @param sessionId id of the session to list files for
     * @param withFiles if true, includes associated files in the session metadata
     * @return list of recordings for the given session
     */
    Optional<RecordingSession> singleSession(String sessionId, boolean withFiles);

    /**
     * Lists all recording sessions available in the repository.
     *
     * @param withFiles if true, includes associated files in the session metadata
     *                  (e.g., recordings, metadata files)
     * @return a list of all recording sessions, each containing metadata and
     * associated recordings
     */
    List<RecordingSession> listSessions(boolean withFiles);

    /**
     * Deletes specific repository files from the repository.
     *
     * @param sessionId         the unique identifier of the recording session
     * @param repositoryFileIds the list of unique identifiers of the repository files to delete
     */
    void deleteRepositoryFiles(String sessionId, List<String> repositoryFileIds);

    /**
     * Deletes a specific recording session and all its associated recordings
     * from the repository.
     *
     * @param sessionId the unique identifier of the recording session to delete
     */
    void deleteSession(String sessionId);

    /**
     * Type of the repository.
     *
     * @return type of the repository.
     */
    RepositoryType type();

    // ========== Recording Files ==========

    /**
     * Get all recordings from a session as compressed files.
     * <p>
     * Compresses JFR → JFR_LZ4 if needed, stores persistently to avoid re-compression.
     * Only returns recordings with FINISHED status.
     * </p>
     *
     * @param sessionId the session ID
     * @return list of paths to compressed recording files
     */
    List<Path> recordings(String sessionId);

    /**
     * Get specific recordings from a session as compressed files.
     * <p>
     * Compresses JFR → JFR_LZ4 if needed, stores persistently to avoid re-compression.
     * Only returns recordings with FINISHED status.
     * </p>
     *
     * @param sessionId    the session ID
     * @param recordingIds list of recording IDs to retrieve
     * @return list of paths to compressed recording files
     */
    List<Path> recordings(String sessionId, List<String> recordingIds);

    // ========== Merge Recordings ==========

    /**
     * Merge all recordings from a session into a single compressed file.
     * <p>
     * Compresses if needed, then merges to temp file.
     * The returned MergedRecording auto-deletes the temp file on close.
     * </p>
     *
     * @param sessionId the session ID
     * @return MergedRecording wrapper (auto-deletes temp file on close)
     */
    MergedRecording mergeRecordings(String sessionId);

    /**
     * Merge specific recordings from a session into a single compressed file.
     * <p>
     * Compresses if needed, then merges to temp file.
     * The returned MergedRecording auto-deletes the temp file on close.
     * </p>
     *
     * @param sessionId    the session ID
     * @param recordingIds list of recording IDs to merge
     * @return MergedRecording wrapper (auto-deletes temp file on close)
     */
    MergedRecording mergeRecordings(String sessionId, List<String> recordingIds);

    // ========== Artifact Files ==========

    /**
     * Get all artifacts (non-recording files) from a session.
     * <p>
     * Artifacts include files like heap dumps, logs, and other non-JFR files.
     * </p>
     *
     * @param sessionId the session ID
     * @return list of paths to artifact files
     */
    List<Path> artifacts(String sessionId);

    /**
     * Get specific artifacts from a session.
     *
     * @param sessionId   the session ID
     * @param artifactIds list of artifact IDs to retrieve
     * @return list of paths to artifact files
     */
    List<Path> artifacts(String sessionId, List<String> artifactIds);

    // ========== Session Compression ==========

    /**
     * Compresses all FINISHED JFR recordings in the session and deletes originals.
     * <p>
     * This is used by the scheduler job to save disk space. Files that are already
     * compressed (JFR_LZ4) are skipped. The original JFR files are deleted after
     * successful compression.
     * </p>
     *
     * @param sessionId the session ID to compress
     * @return number of files compressed
     */
    int compressSession(String sessionId);
}
