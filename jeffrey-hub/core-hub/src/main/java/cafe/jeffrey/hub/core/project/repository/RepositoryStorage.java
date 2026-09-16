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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;

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
     * Lists recording sessions that belong to the given instance. Walks only
     * the matching sessions' directories — does not scan the whole repository.
     *
     * @param instanceId the instance whose sessions should be returned
     * @param withFiles  if true, includes associated files in the session metadata
     * @return recording sessions for the instance, newest first
     */
    List<RecordingSession> listSessionsByInstanceId(String instanceId, boolean withFiles);

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
     * Deletes an instance's directory (including its marker file) from the repository.
     * Removing the on-disk declaration is what makes an instance deletion final: the
     * workspace reconciler re-creates any instance whose directory still exists.
     *
     * @param instanceId the unique identifier of the instance whose directory to delete
     */
    void deleteInstanceDirectory(String instanceId);

    /**
     * Deletes the whole project directory (including its marker file) from the repository.
     * Removing the on-disk declaration is what makes a project deletion final: the
     * workspace reconciler re-creates any project whose directory still exists. A no-op
     * when the project has no repository or the directory is already gone.
     */
    void deleteProjectDirectory();

    /**
     * Lists every session directory physically present under this project's repository
     * path, regardless of whether the database knows about it.
     * <p>
     * This is deliberately the only method here that reads the filesystem without
     * consulting the database first — everything else resolves paths from session rows
     * and therefore cannot observe a directory the database has lost track of. Callers
     * diff this against {@link #listSessions(boolean)} to find orphans; deciding what
     * counts as an orphan is the caller's policy, not the storage's.
     *
     * @return absolute paths of directories that look like session directories
     */
    List<Path> listSessionDirectoriesOnDisk();


    // ========== Recording Files ==========

    /**
     * The paths of a session's closed recording files, as they are on disk.
     *
     * <p>A lookup and nothing else: it does not compress, and the file it names is the file the
     * listing named. Compression belongs to the compression job alone — a reader that receives
     * bytes cannot be told the name changed under it, because the download carries only the
     * bytes and their length.
     *
     * <p>The chunk the profiler is still writing is never among them.
     *
     * @param sessionId    the session ID
     * @param recordingIds the ids to retrieve, or empty for every closed recording of the session
     * @return paths to the recording files, oldest first
     */
    List<Path> recordings(String sessionId, List<String> recordingIds);

    // ========== Artifact Files ==========

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
     * Compresses FINISHED JFR recordings in the session and deletes originals.
     * <p>
     * This is used by the scheduler job to save disk space. Files that are already
     * compressed (JFR_LZ4) are skipped. The original JFR files are deleted after
     * successful compression.
     * <p>
     * For ACTIVE sessions, the latest few recording files are skipped because
     * async-profiler may still be flushing data into them.
     * </p>
     *
     * @param sessionId the session ID to compress
     * @return number of files compressed
     */
    int compressSession(String sessionId);
}
