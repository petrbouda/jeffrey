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

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;

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


    // ========== Files ==========

    /**
     * Where one file of a session is on disk, exactly as it lies.
     *
     * <p>One lookup for every kind of file. There were two — one for recordings, one for
     * artifacts — and by the end they differed only in the word in their refusal. What a file's
     * category decides is what a reader does with it, and the reader knows the category already;
     * it does not need a separate door to be told.
     *
     * <p>Refuses rather than returns nothing, so "not found" means not found. A file the session
     * does not hold, a transient one, one no longer on disk, the chunk the profiler is still
     * writing, and an empty recording are each named with the reason, because a caller that asked
     * for one file and got silence cannot tell which of those happened. An empty <em>artifact</em>
     * is served: emptiness is a statement about parsing, and a log with nothing in it is an
     * answer.
     *
     * @param sessionId the session ID
     * @param fileId    the file's id, as the session's listing reports it
     * @return the path the file has now, which is the name the caller must use for it
     * @throws IllegalArgumentException when the session has no such file, or it is one the hub
     *                                  will not hand over
     */
    Path file(String sessionId, String fileId);

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
